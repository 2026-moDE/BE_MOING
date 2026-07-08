package com.moing.backend.domain.review.service;

import com.moing.backend.domain.notification.entity.Notification;
import com.moing.backend.domain.notification.repository.NotificationRepository;
import com.moing.backend.domain.place.entity.Place;
import com.moing.backend.domain.place.entity.PlaceSubscription;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.place.repository.PlaceSubscriptionRepository;
import com.moing.backend.domain.place.service.CongestionCacheService;
import com.moing.backend.domain.review.dto.ReviewCreateRequest;
import com.moing.backend.domain.review.dto.ReviewCreateResponse;
import com.moing.backend.domain.review.dto.ReviewListResponse;
import com.moing.backend.domain.review.entity.CongestionLevel;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.repository.ReviewHelpfulRepository;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.domain.user.entity.User;
import com.moing.backend.domain.user.repository.UserRepository;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import com.moing.backend.global.infra.FcmService;
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
    private final PlaceSubscriptionRepository placeSubscriptionRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final CongestionCacheService congestionCacheService;
    private final FcmService fcmService;

    // 리뷰 삭제
    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        if (!review.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        reviewRepository.delete(review);
    }

    // 리뷰 작성
    @Transactional
    public ReviewCreateResponse createReview(Long userId, ReviewCreateRequest request) {
        Long placeId = resolveOrCreatePlaceId(request);

        Review review = Review.builder()
                .userId(userId)
                .placeId(placeId)
                .congestionLevel(request.congestionLevel())
                .quickTag(request.quickTag())
                .comment(request.comment())
                .imageUrl(request.imageUrl())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .build();

        ReviewCreateResponse response = ReviewCreateResponse.from(reviewRepository.save(review));
        congestionCacheService.refreshForPlace(placeId);
        sendReviewNotifications(userId, placeId, request.congestionLevel());
        return response;
    }

    private void sendReviewNotifications(Long reviewerId, Long placeId, CongestionLevel congestionLevel) {
        Place place = placeRepository.findById(placeId).orElse(null);
        if (place == null) return;

        String title = switch (congestionLevel) {
            case LOW -> "관심있어 하신 장소가 지금 한산해요!";
            case MEDIUM -> "관심있어 하신 장소가 지금 보통이에요!";
            case HIGH -> "관심있어 하신 장소가 지금 붐벼요!";
        };
        String body = place.getName() + "에 새 리뷰가 등록됐어요!";

        List<PlaceSubscription> subscriptions = placeSubscriptionRepository.findByPlaceId(placeId);
        List<Long> subscriberIds = subscriptions.stream()
                .map(PlaceSubscription::getUserId)
                .filter(id -> !id.equals(reviewerId))
                .toList();

        if (subscriberIds.isEmpty()) return;

        List<User> subscribers = userRepository.findAllById(subscriberIds);
        for (User subscriber : subscribers) {
            notificationRepository.save(Notification.builder()
                    .userId(subscriber.getId())
                    .placeId(placeId)
                    .title(title)
                    .body(body)
                    .build());

            if (subscriber.getFcmToken() != null) {
                fcmService.sendNotification(subscriber.getFcmToken(), title, body);
            }
        }
    }

    // placeId가 있으면 존재 확인, 없으면 새 장소 생성 후 id 반환
    private Long resolveOrCreatePlaceId(ReviewCreateRequest request) {
        if (request.placeId() != null) {
            if (!placeRepository.existsById(request.placeId())) {
                throw new CustomException(ErrorCode.NOT_FOUND);
            }
            return request.placeId();
        }

        if (request.placeName() == null || request.placeAddress() == null
                || request.placeLatitude() == null || request.placeLongitude() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        // 같은 이름의 장소가 이미 존재하면 재사용
        return placeRepository.findByNameAndIsActiveTrue(request.placeName())
                .map(Place::getId)
                .orElseGet(() -> {
                    Place newPlace = Place.builder()
                            .name(request.placeName())
                            .address(request.placeAddress())
                            .latitude(request.placeLatitude())
                            .longitude(request.placeLongitude())
                            .category(request.placeCategory())
                            .source("USER")
                            .build();
                    return placeRepository.save(newPlace).getId();
                });
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
    public ReviewListResponse getArchivedReviews(Long placeId, Long userId, Long cursor, int limit) {
        if (!placeRepository.existsById(placeId)) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }

        LocalDateTime since = LocalDateTime.now().minusHours(72);
        List<Review> reviews = reviewRepository.findArchivedReviews(
                placeId, since, cursor, PageRequest.of(0, limit + 1));

        return buildResponse(reviews, limit, userId, false);
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
                    boolean isMine = userId != null && userId.equals(r.getUserId());
                    return new ReviewListResponse.ReviewItem(
                            r.getId(), r.getImageUrl(), r.getCongestionLevel(),
                            r.getComment(), r.getHelpfulCount(), isHelpful, isMine,
                            userInfo, r.getCreatedAt()
                    );
                })
                .toList();

        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return new ReviewListResponse(items, nextCursor);
    }
}
