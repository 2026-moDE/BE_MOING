package com.moing.backend.domain.user.service;

import com.moing.backend.domain.comment.repository.CommentRepository;
import com.moing.backend.domain.follow.entity.Follow;
import com.moing.backend.domain.follow.entity.FollowStatus;
import com.moing.backend.domain.follow.repository.FollowRepository;
import com.moing.backend.domain.notification.entity.Notification;
import com.moing.backend.domain.notification.repository.NotificationRepository;
import com.moing.backend.domain.place.entity.Place;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.place.repository.PlaceSubscriptionRepository;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.entity.Visibility;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.domain.user.dto.MyReviewListResponse;
import com.moing.backend.domain.user.dto.NotificationListResponse;
import com.moing.backend.domain.user.dto.UserProfileResponse;
import com.moing.backend.domain.user.dto.UserPublicProfileResponse;
import com.moing.backend.domain.user.dto.UserReviewItem;
import com.moing.backend.domain.user.dto.UserReviewListResponse;
import com.moing.backend.domain.user.dto.SubscriptionListResponse;
import com.moing.backend.domain.user.dto.UnreadNotificationResponse;
import com.moing.backend.domain.user.dto.UserUpdateRequest;
import com.moing.backend.domain.user.dto.UserUpdateResponse;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    // 프로필에 미리 보여주는 리뷰 수 (전체 목록은 별도 엔드포인트가 커서로 넘긴다)
    private static final int PROFILE_PREVIEW_LIMIT = 3;
    // 한 번에 내려줄 수 있는 리뷰 수 상한
    private static final int MAX_REVIEW_LIMIT = 100;
    // is_recent 판정 기준 (다른 화면의 "현재 리뷰"와 같은 기준)
    private static final int RECENT_HOURS = 72;

    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final PlaceSubscriptionRepository placeSubscriptionRepository;
    private final PlaceRepository placeRepository;
    private final NotificationRepository notificationRepository;
    private final FollowRepository followRepository;
    private final CommentRepository commentRepository;

    public UserProfileResponse getMyProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        long reviewCount = reviewRepository.countByUserId(userId);
        long placeCount = reviewRepository.countDistinctPlaceIdByUserId(userId);
        long subscriptionCount = placeSubscriptionRepository.countByUserId(userId);
        long friendCount = followRepository.countFriends(userId);

        return new UserProfileResponse(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.getProfileUrl(),
                reviewCount,
                placeCount,
                subscriptionCount,
                friendCount
        );
    }

    /**
     * 타인 프로필 조회.
     *
     * <p>리뷰는 친구 여부와 상관없이 최신 {@value #PROFILE_PREVIEW_LIMIT}개만 미리 보여준다.
     * 전체 목록은 {@link #getUserReviews}가 커서로 넘긴다. 친구 여부로 갈리는 것은 개수가 아니라
     * 친구 공개 리뷰가 섞이는지, created_at과 is_recent를 주는지다.
     *
     * <p>review_count는 미리보기 수가 아니라 보이는 리뷰 전체 개수라, 프론트가 이 값으로
     * "더보기"를 띄울지 판단할 수 있다.
     */
    public UserPublicProfileResponse getUserProfile(Long viewerId, Long targetUserId) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        FollowStatus relation = followRepository.findByFollowerIdAndFollowingId(viewerId, targetUserId)
                .map(Follow::getStatus)
                .orElse(null);
        boolean isFriend = relation == FollowStatus.ACCEPTED;
        // 거절당한 사실은 숨기고 재요청이 가능하도록 NONE으로 노출한다 (친구 검색과 같은 규칙)
        String friendStatus = (relation == null || relation == FollowStatus.REJECTED)
                ? "NONE" : relation.name();

        boolean fullAccess = hasFullAccess(viewerId, targetUserId, isFriend);
        List<Visibility> visibilities = visibleTo(fullAccess);

        List<Review> reviews = reviewRepository.findUserVisibleReviews(
                targetUserId, visibilities, null, PageRequest.of(0, PROFILE_PREVIEW_LIMIT));

        return new UserPublicProfileResponse(
                target.getId(),
                target.getNickname(),
                target.getProfileImageUrl(),
                target.getProfileUrl(),
                followRepository.countFriends(targetUserId),
                reviewRepository.countUserVisibleReviews(targetUserId, visibilities),
                isFriend,
                friendStatus,
                toReviewItems(reviews, fullAccess)
        );
    }

    /**
     * 타인 리뷰 목록 조회 (커서 기반).
     *
     * <p>보이는 범위는 프로필과 같다. 친구가 아니면 전체 공개 리뷰만, created_at은 null.
     */
    public UserReviewListResponse getUserReviews(Long viewerId, Long targetUserId, Long cursor, int limit) {
        userRepository.findById(targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        boolean isFriend = followRepository.findByFollowerIdAndFollowingId(viewerId, targetUserId)
                .map(f -> f.getStatus() == FollowStatus.ACCEPTED)
                .orElse(false);
        boolean fullAccess = hasFullAccess(viewerId, targetUserId, isFriend);

        int safeLimit = Math.min(Math.max(limit, 1), MAX_REVIEW_LIMIT);
        List<Review> reviews = reviewRepository.findUserVisibleReviews(
                targetUserId, visibleTo(fullAccess), cursor, PageRequest.of(0, safeLimit + 1));

        boolean hasNext = reviews.size() > safeLimit;
        List<Review> page = hasNext ? reviews.subList(0, safeLimit) : reviews;
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        return new UserReviewListResponse(toReviewItems(page, fullAccess), nextCursor);
    }

    // 자기 id로 조회하는 경우도 있어(친구 관계가 아니므로) 본인은 따로 통과시킨다
    private boolean hasFullAccess(Long viewerId, Long targetUserId, boolean isFriend) {
        return isFriend || viewerId.equals(targetUserId);
    }

    // 엔티티가 기본값을 넣기 전에 쌓인 리뷰는 visibility가 비어 있다.
    // 조회 쿼리가 전체 공개로 취급하므로 응답에서도 같은 값으로 맞춘다
    private Visibility visibilityOf(Review review) {
        return review.getVisibility() != null ? review.getVisibility() : Visibility.PUBLIC;
    }

    // visibility가 null인 옛 리뷰는 쿼리에서 전체 공개로 취급하므로 여기 넣지 않는다
    private List<Visibility> visibleTo(boolean fullAccess) {
        return fullAccess
                ? List.of(Visibility.PUBLIC, Visibility.FRIENDS)
                : List.of(Visibility.PUBLIC);
    }

    private List<UserReviewItem> toReviewItems(List<Review> reviews, boolean fullAccess) {
        if (reviews.isEmpty()) {
            return List.of();
        }

        // 장소 정보 일괄 조회
        List<Long> placeIds = reviews.stream().map(Review::getPlaceId).distinct().toList();
        Map<Long, Place> placeMap = placeRepository.findAllById(placeIds).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));

        Map<Long, Long> commentCountMap = commentRepository.countMapByReviewIds(
                reviews.stream().map(Review::getId).toList());

        LocalDateTime recentSince = LocalDateTime.now().minusHours(RECENT_HOURS);

        return reviews.stream().map(r -> {
            Place place = placeMap.get(r.getPlaceId());
            UserReviewItem.PlaceInfo placeInfo = place != null
                    ? new UserReviewItem.PlaceInfo(place.getId(), place.getName(), place.getAddress())
                    : new UserReviewItem.PlaceInfo(r.getPlaceId(), null, null);

            // 작성 시각을 감추는 상대에게는 is_recent도 주지 않는다.
            // 최근 여부만으로도 시각이 대략 드러나기 때문이다 (null이면 응답에서 필드가 빠진다)
            LocalDateTime createdAt = fullAccess ? r.getCreatedAt() : null;
            Boolean isRecent = fullAccess ? r.getCreatedAt().isAfter(recentSince) : null;

            return new UserReviewItem(
                    r.getId(), placeInfo, r.getImageUrl(), r.getThumbnailUrl(), r.getThumbnailSmallUrl(),
                    r.getCongestionLevel(), r.getComment(), visibilityOf(r),
                    commentCountMap.getOrDefault(r.getId(), 0L), createdAt, isRecent);
        }).toList();
    }

    public MyReviewListResponse getMyReviews(Long userId, Long cursor, int limit,
                                              Integer year, Integer month) {
        List<Review> reviews = reviewRepository.findMyReviews(
                userId, cursor, year, month, PageRequest.of(0, limit + 1));

        boolean hasNext = reviews.size() > limit;
        List<Review> page = hasNext ? reviews.subList(0, limit) : reviews;
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        // 장소 정보 일괄 조회
        List<Long> placeIds = page.stream().map(Review::getPlaceId).distinct().toList();
        Map<Long, Place> placeMap = placeRepository.findAllById(placeIds).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));

        Map<Long, Long> commentCountMap = commentRepository.countMapByReviewIds(
                page.stream().map(Review::getId).toList());

        List<MyReviewListResponse.MyReviewItem> items = page.stream().map(r -> {
            Place place = placeMap.get(r.getPlaceId());
            MyReviewListResponse.PlaceInfo placeInfo = place != null
                    ? new MyReviewListResponse.PlaceInfo(place.getId(), place.getName(), place.getAddress())
                    : new MyReviewListResponse.PlaceInfo(r.getPlaceId(), null, null);
            return new MyReviewListResponse.MyReviewItem(
                    r.getId(), placeInfo, r.getImageUrl(), r.getThumbnailUrl(), r.getThumbnailSmallUrl(),
                    r.getCongestionLevel(), r.getComment(), visibilityOf(r),
                    commentCountMap.getOrDefault(r.getId(), 0L), r.getCreatedAt());
        }).toList();

        return new MyReviewListResponse(items, nextCursor);
    }

    public NotificationListResponse getMyNotifications(Long userId, Long cursor, int limit) {
        List<Notification> notifications = notificationRepository.findByUserId(
                userId, cursor, PageRequest.of(0, limit + 1));

        boolean hasNext = notifications.size() > limit;
        List<Notification> page = hasNext ? notifications.subList(0, limit) : notifications;
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        // 장소 정보 일괄 조회
        List<Long> placeIds = page.stream()
                .map(Notification::getPlaceId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, Place> placeMap = placeRepository.findAllById(placeIds).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));

        List<NotificationListResponse.NotificationItem> items = page.stream().map(n -> {
            NotificationListResponse.PlaceInfo placeInfo = null;
            if (n.getPlaceId() != null) {
                Place place = placeMap.get(n.getPlaceId());
                if (place != null) {
                    placeInfo = new NotificationListResponse.PlaceInfo(place.getId(), place.getName());
                }
            }
            return new NotificationListResponse.NotificationItem(
                    n.getId(),
                    n.getType() != null ? n.getType().name() : null,
                    n.getTitle(), n.getBody(), placeInfo, n.getReviewId(),
                    n.isRead(), n.getCreatedAt());
        }).toList();

        return new NotificationListResponse(items, nextCursor);
    }

    public UnreadNotificationResponse hasUnreadNotifications(Long userId) {
        return new UnreadNotificationResponse(notificationRepository.existsUnread(userId));
    }

    @Transactional
    public UserUpdateResponse updateMyProfile(Long userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (request.nickname() != null && !request.nickname().equals(user.getNickname())) {
            if (userRepository.existsByNicknameAndDeletedAtIsNull(request.nickname())) {
                throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
            }
            user.updateNickname(request.nickname());
        }

        if (request.profileImageUrl() != null) {
            // 빈 문자열은 null로 정규화해 저장 (프로필 이미지 제거로 동작)
            user.updateProfileImageUrl(request.profileImageUrl().isBlank() ? null : request.profileImageUrl());
        }

        return new UserUpdateResponse(user.getNickname(), user.getProfileImageUrl());
    }

    public SubscriptionListResponse getMySubscriptions(Long userId) {
        var subscriptions = placeSubscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId);

        List<Long> placeIds = subscriptions.stream()
                .map(com.moing.backend.domain.place.entity.PlaceSubscription::getPlaceId)
                .distinct()
                .toList();
        Map<Long, Place> placeMap = placeRepository.findAllById(placeIds).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));

        List<SubscriptionListResponse.SubscriptionItem> items = subscriptions.stream().map(s -> {
            Place place = placeMap.get(s.getPlaceId());
            var topReview = reviewRepository
                    .findTopWithImageAllTimeByPlaceId(s.getPlaceId(), PageRequest.of(0, 1))
                    .stream().findFirst();
            // 썸네일이 없는 옛 이미지(original/ 폴더 밖 업로드)는 원본으로 폴백
            String thumbnail = topReview
                    .map(r -> r.getThumbnailUrl() != null ? r.getThumbnailUrl() : r.getImageUrl())
                    .orElse(null);
            String thumbnailSmall = topReview.map(Review::getThumbnailSmallUrl).orElse(null);
            SubscriptionListResponse.PlaceInfo placeInfo = place != null
                    ? new SubscriptionListResponse.PlaceInfo(place.getId(), place.getName(), place.getAddress(), thumbnail, thumbnailSmall)
                    : new SubscriptionListResponse.PlaceInfo(s.getPlaceId(), null, null, thumbnail, thumbnailSmall);
            return new SubscriptionListResponse.SubscriptionItem(
                    s.getId(), placeInfo, true, s.getCreatedAt());
        }).toList();

        return new SubscriptionListResponse(items);
    }
}
