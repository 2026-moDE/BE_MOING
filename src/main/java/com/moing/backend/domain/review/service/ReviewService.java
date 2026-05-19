package com.moing.backend.domain.review.service;

import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.review.dto.ReviewCreateRequest;
import com.moing.backend.domain.review.dto.ReviewCreateResponse;
import com.moing.backend.domain.review.dto.ReviewListResponse;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.repository.ReviewHelpfulRepository;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.domain.user.entity.User;
import com.moing.backend.domain.user.repository.UserRepository;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewHelpfulRepository reviewHelpfulRepository;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;

    // 리뷰 작성
    @Transactional
    public ReviewCreateResponse createReview(Long userId, ReviewCreateRequest request) {
        if (!placeRepository.existsById(request.placeId())) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }

        Review review = Review.builder()
                .userId(userId)
                .placeId(request.placeId())
                .congestionLevel(request.congestionLevel())
                .quickTag(request.quickTag())
                .comment(request.comment())
                .imageUrl(request.imageUrl())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .build();

        return ReviewCreateResponse.from(reviewRepository.save(review));
    }

    // 현재 리뷰 목록 (72h 이내, 커서 기반)
    @Transactional(readOnly = true)
    public ReviewListResponse getCurrentReviews(Long placeId, Long userId, Long cursor, int limit) {
        if (!placeRepository.existsById(placeId)) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }

        LocalDateTime since = LocalDateTime.now().minusHours(72);
        List<Review> reviews = reviewRepository.findCurrentReviews(
                placeId, since, cursor, PageRequest.of(0, limit + 1));

        return buildResponse(reviews, limit, userId, true);
    }

    // 과거 리뷰 목록 (72h 경과, 커서 기반)
    @Transactional(readOnly = true)
    public ReviewListResponse getArchivedReviews(Long placeId, Long cursor, int limit) {
        if (!placeRepository.existsById(placeId)) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }

        LocalDateTime since = LocalDateTime.now().minusHours(72);
        List<Review> reviews = reviewRepository.findArchivedReviews(
                placeId, since, cursor, PageRequest.of(0, limit + 1));

        return buildResponse(reviews, limit, null, false);
    }

    private ReviewListResponse buildResponse(List<Review> reviews, int limit, Long userId, boolean includeIsHelpful) {
        boolean hasNext = reviews.size() > limit;
        List<Review> page = hasNext ? reviews.subList(0, limit) : reviews;

        List<Long> userIds = page.stream().map(Review::getUserId).distinct().toList();
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Set<Long> helpfulReviewIds = Set.of();
        if (includeIsHelpful && userId != null && !page.isEmpty()) {
            List<Long> reviewIds = page.stream().map(Review::getId).toList();
            helpfulReviewIds = reviewHelpfulRepository.findHelpfulReviewIds(userId, reviewIds);
        }

        final Set<Long> finalHelpfulIds = helpfulReviewIds;
        List<ReviewListResponse.ReviewItem> items = page.stream()
                .map(r -> {
                    User user = userMap.get(r.getUserId());
                    ReviewListResponse.UserInfo userInfo = user != null
                            ? new ReviewListResponse.UserInfo(user.getNickname(), user.getProfileImageUrl())
                            : new ReviewListResponse.UserInfo("알 수 없음", null);
                    Boolean isHelpful = includeIsHelpful ? finalHelpfulIds.contains(r.getId()) : null;
                    return new ReviewListResponse.ReviewItem(
                            r.getId(), r.getImageUrl(), r.getCongestionLevel(),
                            r.getComment(), r.getHelpfulCount(), isHelpful,
                            userInfo, r.getCreatedAt()
                    );
                })
                .toList();

        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return new ReviewListResponse(items, nextCursor);
    }
}
