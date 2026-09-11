package com.moing.backend.domain.review.service;

import com.moing.backend.domain.comment.repository.CommentModerationRepository;
import com.moing.backend.domain.comment.repository.CommentRepository;
import com.moing.backend.domain.follow.entity.FollowStatus;
import com.moing.backend.domain.follow.repository.FollowRepository;
import com.moing.backend.domain.reaction.repository.ReactionRepository;
import com.moing.backend.domain.notification.entity.NotificationType;
import com.moing.backend.domain.notification.service.NotificationService;
import com.moing.backend.domain.place.entity.Place;
import com.moing.backend.domain.place.entity.PlaceSubscription;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.place.repository.PlaceSubscriptionRepository;
import com.moing.backend.domain.place.service.CongestionCacheService;
import com.moing.backend.domain.review.dto.ReviewCreateRequest;
import com.moing.backend.domain.review.dto.ReviewCreateResponse;
import com.moing.backend.domain.review.dto.ReviewListResponse;
import com.moing.backend.domain.review.dto.ReviewReportRequest;
import com.moing.backend.domain.review.dto.ReviewUpdateRequest;
import com.moing.backend.domain.review.entity.ReviewReport;
import com.moing.backend.domain.review.repository.ReviewReportRepository;
import com.moing.backend.domain.review.dto.ReviewDetailResponse;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.entity.Visibility;
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
    private final ReviewReportRepository reviewReportRepository;
    private final PlaceRepository placeRepository;
    private final PlaceSubscriptionRepository placeSubscriptionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final CongestionCacheService congestionCacheService;
    private final FollowRepository followRepository;
    private final CommentRepository commentRepository;
    private final CommentModerationRepository commentModerationRepository;
    private final ReactionRepository reactionRepository;

    // 리뷰 상세 조회
    @Transactional(readOnly = true)
    public ReviewDetailResponse getReviewDetail(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        if (review.isBlinded()) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }

        boolean isMine = userId.equals(review.getUserId());
        boolean isFriend = !isMine && isFriend(userId, review.getUserId());

        // 친구 공개 리뷰는 작성자 본인과 친구만 볼 수 있다
        if (review.getVisibility() == Visibility.FRIENDS && !isMine && !isFriend) {
            throw new CustomException(ErrorCode.NOT_FRIEND_REVIEW);
        }

        User author = userRepository.findById(review.getUserId()).orElse(null);
        ReviewDetailResponse.UserInfo userInfo = author != null
                ? new ReviewDetailResponse.UserInfo(author.getId(), author.getNickname(), author.getProfileImageUrl(), author.getProfileUrl())
                : new ReviewDetailResponse.UserInfo(review.getUserId(), "알 수 없음", null, null);

        String placeName = placeRepository.findById(review.getPlaceId())
                .map(Place::getName)
                .orElse(null);

        return new ReviewDetailResponse(
                review.getId(),
                review.getPlaceId(),
                placeName,
                review.getImageUrl(),
                review.getThumbnailUrl(),
                review.getThumbnailSmallUrl(),
                review.getCongestionLevel(),
                review.getComment(),
                isMine,
                review.getVisibility() != null ? review.getVisibility() : Visibility.PUBLIC,
                isFriend,
                userInfo,
                review.getCreatedAt()
        );
    }

    // 서로 친구(ACCEPTED)인지 확인
    private boolean isFriend(Long userId, Long targetUserId) {
        return followRepository.existsByFollowerIdAndFollowingIdAndStatusIn(
                userId, targetUserId, List.of(FollowStatus.ACCEPTED));
    }

    // 리뷰 삭제
    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        if (!review.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        Long placeId = review.getPlaceId();

        // DB에 FK가 없어 cascade가 걸리지 않는다. 남겨두면 어느 화면에도 안 나오는 고아 행이 되므로
        // 자식부터 직접 지운다. 같은 트랜잭션이라 중간에 실패하면 전부 롤백된다.
        reactionRepository.deleteAllByReviewId(reviewId);

        // 답글도 review_id를 갖고 있어 최상위 댓글과 함께 지워진다
        List<Long> commentIds = commentRepository.findIdsByReviewId(reviewId);
        if (!commentIds.isEmpty()) {
            commentModerationRepository.deleteAllByCommentIdIn(commentIds);
            commentRepository.deleteAllByReviewId(reviewId);
        }

        reviewRepository.delete(review);

        // 지운 리뷰가 혼잡도에 남지 않도록 즉시 갱신한다.
        // 마지막 리뷰였다면 refreshForPlace가 캐시 행 자체를 지운다.
        congestionCacheService.refreshForPlace(placeId);
    }

    // 리뷰 수정
    @Transactional
    public void updateReview(Long userId, Long reviewId, ReviewUpdateRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        if (!review.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        review.updateComment(request.comment());
    }

    // 리뷰 신고
    @Transactional
    public void reportReview(Long userId, Long reviewId, ReviewReportRequest request) {
        if (!reviewRepository.existsById(reviewId)) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }

        if (reviewReportRepository.existsByReviewIdAndReporterId(reviewId, userId)) {
            throw new CustomException(ErrorCode.DUPLICATE);
        }

        reviewReportRepository.save(ReviewReport.builder()
                .reviewId(reviewId)
                .reporterId(userId)
                .reason(request.reason())
                .detail(request.detail())
                .build());
    }

    // 리뷰 작성
    @Transactional
    public ReviewCreateResponse createReview(Long userId, ReviewCreateRequest request) {
        Long placeId = resolveOrCreatePlaceId(request);

        Review review = Review.builder()
                .userId(userId)
                .placeId(placeId)
                .congestionLevel(request.congestionLevel())
                .comment(request.comment())
                // 빈 문자열은 null로 정규화 (프론트 폴백 체인이 null 기준으로 동작)
                .imageUrl(request.imageUrl() != null && !request.imageUrl().isBlank() ? request.imageUrl() : null)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .visibility(request.visibility())
                .build();

        Review saved = reviewRepository.save(review);
        ReviewCreateResponse response = ReviewCreateResponse.from(saved);
        congestionCacheService.refreshForPlace(placeId);
        sendReviewNotifications(userId, placeId, saved.getId());
        return response;
    }

    private void sendReviewNotifications(Long reviewerId, Long placeId, Long reviewId) {
        Place place = placeRepository.findById(placeId).orElse(null);
        if (place == null) return;

        String title = "관심있어 하신 장소에 새로운 리뷰가 작성됐어요";
        String body = "관심 장소로 등록하신 '" + place.getName() + "'에 새로운 리뷰가 작성됐어요.";

        List<PlaceSubscription> subscriptions = placeSubscriptionRepository.findByPlaceId(placeId);
        List<Long> subscriberIds = subscriptions.stream()
                .map(PlaceSubscription::getUserId)
                .filter(id -> !id.equals(reviewerId))
                .toList();

        if (subscriberIds.isEmpty()) return;

        List<User> subscribers = userRepository.findAllById(subscriberIds);
        for (User subscriber : subscribers) {
            notificationService.send(subscriber, NotificationType.REVIEW, placeId, reviewId, title, body);
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
        return placeRepository.findFirstByNameAndIsActiveTrueOrderByIdAsc(request.placeName())
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
                placeId, since, cursor, userId, PageRequest.of(0, limit + 1));

        return buildResponse(reviews, limit, userId);
    }

    // 과거 리뷰 목록 (72h 경과, 커서 기반)
    @Transactional(readOnly = true)
    public ReviewListResponse getArchivedReviews(Long placeId, Long userId, Long cursor, int limit) {
        if (!placeRepository.existsById(placeId)) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }

        LocalDateTime since = LocalDateTime.now().minusHours(72);
        List<Review> reviews = reviewRepository.findArchivedReviews(
                placeId, since, cursor, userId, PageRequest.of(0, limit + 1));

        return buildResponse(reviews, limit, userId);
    }

    private ReviewListResponse buildResponse(List<Review> reviews, int limit, Long userId) {
        boolean hasNext = reviews.size() > limit;
        List<Review> page = hasNext ? reviews.subList(0, limit) : reviews;

        List<Long> userIds = page.stream().map(Review::getUserId).distinct().toList();
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 작성자별 exists 대신 한 번에 조회해 N+1을 피한다
        List<Long> otherUserIds = userId == null ? List.of()
                : userIds.stream().filter(id -> !userId.equals(id)).toList();
        Set<Long> friendIds = otherUserIds.isEmpty() ? Set.of()
                : Set.copyOf(followRepository.findAcceptedFollowingIds(userId, otherUserIds));

        List<ReviewListResponse.ReviewItem> items = page.stream()
                .map(r -> {
                    User user = userMap.get(r.getUserId());
                    ReviewListResponse.UserInfo userInfo = user != null
                            ? new ReviewListResponse.UserInfo(user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getProfileUrl())
                            : new ReviewListResponse.UserInfo(r.getUserId(), "알 수 없음", null, null);
                    boolean isMine = userId != null && userId.equals(r.getUserId());
                    // 상세 조회와 동일하게 내 리뷰면 is_friend는 false
                    boolean isFriend = !isMine && friendIds.contains(r.getUserId());
                    return new ReviewListResponse.ReviewItem(
                            r.getId(), r.getImageUrl(), r.getThumbnailUrl(), r.getThumbnailSmallUrl(),
                            r.getCongestionLevel(), r.getComment(), isMine,
                            r.getVisibility() != null ? r.getVisibility() : Visibility.PUBLIC,
                            isFriend, userInfo, r.getCreatedAt()
                    );
                })
                .toList();

        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return new ReviewListResponse(items, nextCursor);
    }
}
