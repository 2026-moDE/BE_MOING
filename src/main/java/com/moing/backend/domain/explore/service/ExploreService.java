package com.moing.backend.domain.explore.service;

import com.moing.backend.domain.explore.dto.ExploreReviewResponse;
import com.moing.backend.domain.place.entity.Place;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.domain.user.entity.User;
import com.moing.backend.domain.user.repository.UserRepository;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExploreService {

    // 다른 화면의 "현재 리뷰"와 같은 기준
    private static final int RECENT_HOURS = 72;

    private static final int DEFAULT_RADIUS = 2000;
    private static final int MIN_RADIUS = 100;
    private static final int MAX_RADIUS = 5000;

    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 50;

    private static final int SEED_LENGTH = 8;

    private final ReviewRepository reviewRepository;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;

    /**
     * 기준 좌표 반경 내 실시간 리뷰 사진 그리드.
     *
     * <p>순서는 seed로 고정된 랜덤이다. seed가 없으면 새로 만들어 응답에 담는다.
     * 정렬 키가 해시라 id 커서를 쓸 수 없어 offset으로 넘긴다.
     */
    public ExploreReviewResponse getExploreReviews(Long userId, Double latitude, Double longitude,
                                                   Integer radius, String seed,
                                                   Integer offset, Integer limit) {
        if (latitude == null || longitude == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        int safeRadius = radius != null ? radius : DEFAULT_RADIUS;
        if (safeRadius < MIN_RADIUS || safeRadius > MAX_RADIUS) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        int safeLimit = limit != null ? limit : DEFAULT_LIMIT;
        // 0 이하를 그대로 넘기면 LIMIT 절에서 터지므로 범위 밖으로 함께 막는다
        if (safeLimit < 1 || safeLimit > MAX_LIMIT) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        int safeOffset = offset != null ? offset : 0;
        if (safeOffset < 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        // 한 탐색 세션의 순서를 고정하는 값. 첫 요청에는 없으니 서버가 만든다
        String safeSeed = StringUtils.hasText(seed)
                ? seed
                : UUID.randomUUID().toString().substring(0, SEED_LENGTH);

        LocalDateTime since = LocalDateTime.now().minusHours(RECENT_HOURS);

        long total = reviewRepository.countExploreReviews(
                latitude, longitude, safeRadius, since, userId);

        List<Object[]> rows = reviewRepository.findExploreReviewIds(
                latitude, longitude, safeRadius, since, userId, safeSeed, safeLimit, safeOffset);

        ExploreReviewResponse.Center center =
                new ExploreReviewResponse.Center(latitude, longitude, safeRadius);

        if (rows.isEmpty()) {
            return new ExploreReviewResponse(safeSeed, center, total, null, List.of());
        }

        // 해시 순서는 쿼리가 정했으므로 거리와 순서를 그대로 들고 간다
        List<Long> reviewIds = new ArrayList<>(rows.size());
        Map<Long, Integer> distanceMap = new HashMap<>();
        for (Object[] row : rows) {
            Long reviewId = ((Number) row[0]).longValue();
            reviewIds.add(reviewId);
            distanceMap.put(reviewId, (int) Math.round(((Number) row[1]).doubleValue()));
        }

        // 리뷰·장소·작성자를 각각 한 번에 조회해 N+1을 피한다
        Map<Long, Review> reviewMap = reviewRepository.findAllById(reviewIds).stream()
                .collect(Collectors.toMap(Review::getId, Function.identity()));

        List<Review> page = reviewIds.stream().map(reviewMap::get).filter(Objects::nonNull).toList();

        Map<Long, Place> placeMap = placeRepository.findAllById(
                        page.stream().map(Review::getPlaceId).distinct().toList()).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));

        Map<Long, User> userMap = userRepository.findAllById(
                        page.stream().map(Review::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<ExploreReviewResponse.ReviewItem> items = page.stream().map(r -> {
            Place place = placeMap.get(r.getPlaceId());
            ExploreReviewResponse.PlaceInfo placeInfo = place != null
                    ? new ExploreReviewResponse.PlaceInfo(place.getId(), place.getName(), place.getAddress())
                    : new ExploreReviewResponse.PlaceInfo(r.getPlaceId(), null, null);

            // 탈퇴한 작성자는 @SQLRestriction으로 조회에서 빠진다 (리뷰 목록과 같은 처리)
            User author = userMap.get(r.getUserId());
            ExploreReviewResponse.UserInfo userInfo = author != null
                    ? new ExploreReviewResponse.UserInfo(author.getId(), author.getNickname(),
                            author.getProfileImageUrl(), author.getProfileUrl())
                    : new ExploreReviewResponse.UserInfo(r.getUserId(), "알 수 없음", null, null);

            return new ExploreReviewResponse.ReviewItem(
                    r.getId(), r.getImageUrl(), r.getThumbnailUrl(), r.getThumbnailSmallUrl(),
                    r.getCongestionLevel(), r.getComment(),
                    distanceMap.getOrDefault(r.getId(), 0),
                    placeInfo, userInfo, r.getCreatedAt());
        }).toList();

        int consumed = safeOffset + items.size();
        Integer nextOffset = consumed < total ? consumed : null;

        return new ExploreReviewResponse(safeSeed, center, total, nextOffset, items);
    }
}
