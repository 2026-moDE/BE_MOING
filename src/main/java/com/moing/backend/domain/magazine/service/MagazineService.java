package com.moing.backend.domain.magazine.service;

import com.moing.backend.domain.block.service.BlockService;
import com.moing.backend.domain.congestion.dto.CongestionResponse;
import com.moing.backend.domain.congestion.service.CongestionService;
import com.moing.backend.domain.magazine.dto.MagazineDetailResponse;
import com.moing.backend.domain.magazine.dto.MagazineListResponse;
import com.moing.backend.domain.magazine.entity.Magazine;
import com.moing.backend.domain.magazine.entity.MagazineItem;
import com.moing.backend.domain.magazine.entity.MagazineRule;
import com.moing.backend.domain.magazine.repository.MagazineItemRepository;
import com.moing.backend.domain.magazine.repository.MagazineRepository;
import com.moing.backend.domain.place.entity.Place;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.entity.Visibility;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.domain.user.entity.User;
import com.moing.backend.domain.user.repository.UserRepository;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 홈 바텀시트 매거진 조회.
 *
 * <p>묶음은 {@link MagazineBatchService}가 하루 1회 만들어 두고, 여기서는 읽기만 한다.
 * 조회 시점에 하는 일은 두 가지다 — 규칙별 노출 조건 판단, 그리고 배치 이후 사라진 리뷰 제외.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MagazineService {

    private static final int DEFAULT_LIMIT = 10;

    /** LATE_NIGHT 묶음을 노출하기 시작하는 시각(KST) */
    private static final int EVENING_FROM_HOUR = 20;

    /** 서울시가 비·눈이 없을 때 내려주는 값 */
    private static final String NO_PRECIPITATION = "없음";

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MagazineRepository magazineRepository;
    private final MagazineItemRepository magazineItemRepository;
    private final ReviewRepository reviewRepository;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;
    private final CongestionService congestionService;
    private final BlockService blockService;
    private final Clock clock;

    /**
     * 노출 조건을 통과한 묶음 목록.
     *
     * <p>priority 높은 순, 같으면 아이템 많은 순이다.
     */
    public MagazineListResponse getMagazines(Long userId, Double latitude, Double longitude, Integer limit) {
        if (latitude == null || longitude == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        int safeLimit = limit != null ? limit : DEFAULT_LIMIT;
        if (safeLimit < 1) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        List<Magazine> magazines = magazineRepository.findAllByOrderByPriorityDescIdAsc().stream()
                .filter(m -> isVisible(m.getRuleKey(), latitude, longitude))
                .toList();

        if (magazines.isEmpty()) {
            return new MagazineListResponse(List.of());
        }

        // 묶음·아이템·리뷰를 각각 한 번에 조회해 N+1을 피한다 (규칙이 다섯 개라 전체가 작다)
        Map<Long, List<MagazineItem>> itemsByMagazine = magazineItemRepository
                .findByMagazineIdInOrderByMagazineIdAscSortOrderAsc(
                        magazines.stream().map(Magazine::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(MagazineItem::getMagazineId));

        Map<Long, Review> reviewMap = loadVisibleReviews(userId, itemsByMagazine.values().stream()
                .flatMap(List::stream)
                .map(MagazineItem::getReviewId)
                .distinct()
                .toList());

        List<MagazineListResponse.MagazineItem> cards = new ArrayList<>(magazines.size());
        for (Magazine magazine : magazines) {
            // sort_order 순서라 첫 번째가 커버다
            List<Review> visible = itemsByMagazine.getOrDefault(magazine.getId(), List.of()).stream()
                    .map(item -> reviewMap.get(item.getReviewId()))
                    .filter(Objects::nonNull)
                    .toList();

            // 남은 리뷰가 없으면 열어도 404라 카드도 내리지 않는다
            if (visible.isEmpty()) {
                continue;
            }

            cards.add(new MagazineListResponse.MagazineItem(
                    magazine.getId(),
                    magazine.getTitle(),
                    coverImageUrl(visible.get(0)),
                    visible.size()));
        }

        // priority는 이미 쿼리에서 정렬됐다. 같은 priority 안에서만 아이템 수로 다시 세운다
        Map<Long, Integer> priorityByMagazine = magazines.stream()
                .collect(Collectors.toMap(Magazine::getId, Magazine::getPriority));

        return new MagazineListResponse(cards.stream()
                .sorted(Comparator
                        .comparingInt((MagazineListResponse.MagazineItem c) ->
                                priorityByMagazine.get(c.id())).reversed()
                        .thenComparing(Comparator.comparingInt(
                                MagazineListResponse.MagazineItem::itemCount).reversed()))
                .limit(safeLimit)
                .toList());
    }

    /**
     * 묶음 상세. 아이템을 {@code sort_order} 순서로 내려준다.
     *
     * @param latitude  없으면 distance를 null로 둔다
     * @throws CustomException 묶음이 없거나 남은 리뷰가 하나도 없으면 404
     */
    public MagazineDetailResponse getMagazineDetail(Long userId, Long magazineId, Double latitude, Double longitude) {
        Magazine magazine = magazineRepository.findById(magazineId)
                .orElseThrow(() -> new CustomException(ErrorCode.MAGAZINE_NOT_FOUND));

        List<MagazineItem> items = magazineItemRepository
                .findByMagazineIdOrderBySortOrderAsc(magazineId);

        Map<Long, Review> reviewMap = loadVisibleReviews(userId,
                items.stream().map(MagazineItem::getReviewId).toList());

        // 배치 이후 지워지거나 블라인드된 리뷰, 차단한 작성자의 리뷰는 빠진다
        List<Review> reviews = items.stream()
                .map(item -> reviewMap.get(item.getReviewId()))
                .filter(Objects::nonNull)
                .toList();

        if (reviews.isEmpty()) {
            throw new CustomException(ErrorCode.MAGAZINE_NOT_FOUND);
        }

        Map<Long, Place> placeMap = placeRepository.findAllById(
                        reviews.stream().map(Review::getPlaceId).distinct().toList()).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));

        Map<Long, User> userMap = userRepository.findAllById(
                        reviews.stream().map(Review::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<MagazineDetailResponse.Item> responseItems = reviews.stream()
                .map(r -> toItem(r, placeMap, userMap, latitude, longitude))
                .toList();

        return new MagazineDetailResponse(magazine.getId(), magazine.getTitle(), responseItems);
    }

    /**
     * 아이템 리뷰를 한 번에 읽고 노출 가능한 것만 남긴다.
     *
     * <p>지워진 리뷰는 조회 결과에서 빠지고, 블라인드된 리뷰는 여기서 걸러낸다.
     * 친구 공개 리뷰는 배치가 이미 제외하지만, 매거진은 불특정 사용자에게 노출되므로
     * 읽는 쪽에서도 한 번 더 막는다.
     *
     * <p>차단은 조회자별로 갈리므로 배치가 미리 걸러둘 수 없고 여기서만 걸러진다.
     * 묶음은 아이템이 많아도 수십 건이라 메모리에서 빼도 된다 — 커서로 넘기는 목록과 달리
     * 몇 건이 빠져도 "다음 페이지"가 어긋날 일이 없다.
     */
    private Map<Long, Review> loadVisibleReviews(Long viewerId, List<Long> reviewIds) {
        if (reviewIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> blockedIds = blockService.getRelatedUserIds(viewerId);
        return reviewRepository.findAllById(reviewIds).stream()
                .filter(r -> !r.isBlinded())
                .filter(r -> r.getVisibility() != Visibility.FRIENDS)
                .filter(r -> !blockedIds.contains(r.getUserId()))
                .collect(Collectors.toMap(Review::getId, Function.identity()));
    }

    /** 썸네일이 없는 옛 이미지(original/ 폴더 밖 업로드)는 원본으로 폴백한다 */
    private String coverImageUrl(Review review) {
        return review.getThumbnailUrl() != null ? review.getThumbnailUrl() : review.getImageUrl();
    }

    private MagazineDetailResponse.Item toItem(Review review,
                                               Map<Long, Place> placeMap,
                                               Map<Long, User> userMap,
                                               Double latitude,
                                               Double longitude) {
        Place place = placeMap.get(review.getPlaceId());
        MagazineDetailResponse.PlaceInfo placeInfo = place != null
                ? new MagazineDetailResponse.PlaceInfo(place.getId(), place.getName(), place.getAddress())
                : new MagazineDetailResponse.PlaceInfo(review.getPlaceId(), null, null);

        // 탈퇴한 작성자는 @SQLRestriction으로 조회에서 빠진다 (리뷰 목록과 같은 처리)
        User author = userMap.get(review.getUserId());
        MagazineDetailResponse.UserInfo userInfo = author != null
                ? new MagazineDetailResponse.UserInfo(
                        author.getId(), author.getNickname(), author.getProfileImageUrl())
                : new MagazineDetailResponse.UserInfo(review.getUserId(), "알 수 없음", null);

        return new MagazineDetailResponse.Item(
                review.getId(),
                review.getImageUrl(),
                review.getThumbnailUrl(),
                review.getComment(),
                review.getCongestionLevel(),
                review.getCreatedAt(),
                distance(review, latitude, longitude),
                placeInfo,
                userInfo);
    }

    /** 요청 좌표나 리뷰 좌표가 없으면 null. 0으로 두면 "바로 옆"과 구분이 안 된다 */
    private Integer distance(Review review, Double latitude, Double longitude) {
        if (latitude == null || longitude == null
                || review.getLatitude() == null || review.getLongitude() == null) {
            return null;
        }
        return calculateDistance(latitude, longitude,
                review.getLatitude().doubleValue(), review.getLongitude().doubleValue());
    }

    // Haversine 공식으로 두 좌표 간 거리(m) 반환
    private int calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int EARTH_RADIUS = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return (int) Math.round(EARTH_RADIUS * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a)));
    }

    /** 규칙의 노출 조건을 지금 만족하는지 */
    private boolean isVisible(MagazineRule rule, double latitude, double longitude) {
        return switch (rule.getDisplay()) {
            case ALWAYS -> true;
            case EVENING -> ZonedDateTime.now(clock).withZoneSameInstant(KST).getHour() >= EVENING_FROM_HOUR;
            case RAINING -> isRaining(latitude, longitude);
        };
    }

    /**
     * 기준 좌표에 비나 눈이 오는지.
     *
     * <p>날씨를 모를 때는 노출하지 않는다 — 맑은 날 "비 오는 날" 묶음이 뜨는 쪽이
     * 비 오는 날 한 묶음이 빠지는 쪽보다 어색하다.
     */
    private boolean isRaining(double latitude, double longitude) {
        CongestionResponse.Weather weather = congestionService.getNearbyWeather(latitude, longitude);

        if (weather == null || weather.precipitationType() == null) {
            return false;
        }
        return !NO_PRECIPITATION.equals(weather.precipitationType());
    }
}
