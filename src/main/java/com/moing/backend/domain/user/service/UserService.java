package com.moing.backend.domain.user.service;

import com.moing.backend.domain.follow.repository.FollowRepository;
import com.moing.backend.domain.notification.entity.Notification;
import com.moing.backend.domain.notification.repository.NotificationRepository;
import com.moing.backend.domain.place.entity.Place;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.place.repository.PlaceSubscriptionRepository;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.domain.user.dto.MyReviewListResponse;
import com.moing.backend.domain.user.dto.NotificationListResponse;
import com.moing.backend.domain.user.dto.UserProfileResponse;
import com.moing.backend.domain.user.dto.SubscriptionListResponse;
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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final PlaceSubscriptionRepository placeSubscriptionRepository;
    private final PlaceRepository placeRepository;
    private final NotificationRepository notificationRepository;
    private final FollowRepository followRepository;

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

        List<MyReviewListResponse.MyReviewItem> items = page.stream().map(r -> {
            Place place = placeMap.get(r.getPlaceId());
            MyReviewListResponse.PlaceInfo placeInfo = place != null
                    ? new MyReviewListResponse.PlaceInfo(place.getId(), place.getName(), place.getAddress())
                    : new MyReviewListResponse.PlaceInfo(r.getPlaceId(), null, null);
            return new MyReviewListResponse.MyReviewItem(
                    r.getId(), placeInfo, r.getImageUrl(), r.getThumbnailUrl(), r.getThumbnailSmallUrl(),
                    r.getCongestionLevel(), r.getComment(), r.getHelpfulCount(), r.getCreatedAt());
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
                    n.getTitle(), n.getBody(), placeInfo,
                    n.isRead(), n.getCreatedAt());
        }).toList();

        return new NotificationListResponse(items, nextCursor);
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
