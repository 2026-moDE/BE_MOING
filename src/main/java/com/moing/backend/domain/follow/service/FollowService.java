package com.moing.backend.domain.follow.service;

import com.moing.backend.domain.follow.dto.FriendFeedResponse;
import com.moing.backend.domain.follow.dto.FriendRequestResponse;
import com.moing.backend.domain.follow.dto.FriendResponse;
import com.moing.backend.domain.follow.dto.UserSearchResponse;
import com.moing.backend.domain.follow.entity.Follow;
import com.moing.backend.domain.follow.entity.FollowStatus;
import com.moing.backend.domain.follow.repository.FollowRepository;
import com.moing.backend.domain.notification.entity.NotificationType;
import com.moing.backend.domain.notification.service.NotificationService;
import com.moing.backend.domain.place.entity.Place;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.domain.user.entity.User;
import com.moing.backend.domain.user.repository.UserRepository;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moing.backend.domain.follow.util.ChosungUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ReviewRepository reviewRepository;
    private final PlaceRepository placeRepository;

    private static final int SEARCH_LIMIT = 20;
    private static final int FEED_MAX_LIMIT = 50;

    @Transactional
    public void sendFollowRequest(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new CustomException(ErrorCode.SELF_FOLLOW_NOT_ALLOWED);
        }

        User target = userRepository.findById(followingId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        var existing = followRepository.findByFollowerIdAndFollowingId(followerId, followingId);
        if (existing.isPresent()) {
            Follow follow = existing.get();
            if (follow.getStatus() == FollowStatus.PENDING || follow.getStatus() == FollowStatus.ACCEPTED) {
                throw new CustomException(ErrorCode.ALREADY_FOLLOWING);
            }
            // REJECTED → 다시 PENDING으로 변경
            follow.toPending();
        } else {
            Follow follow = Follow.builder()
                    .followerId(followerId)
                    .followingId(followingId)
                    .status(FollowStatus.PENDING)
                    .build();
            try {
                // 동시 중복 요청 시 unique constraint 위반을 409로 변환
                followRepository.saveAndFlush(follow);
            } catch (DataIntegrityViolationException e) {
                throw new CustomException(ErrorCode.ALREADY_FOLLOWING);
            }
        }

        User requester = userRepository.findById(followerId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        sendFriendNotification(target, NotificationType.FRIEND_REQUEST,
                "새로운 친구 요청을 확인해보세요",
                requester.getNickname() + "님이 친구를 요청했어요");
    }

    @Transactional(readOnly = true)
    public List<FriendRequestResponse> getReceivedRequests(Long userId) {
        List<Follow> follows = followRepository.findByFollowingIdAndStatus(userId, FollowStatus.PENDING);

        List<Long> requesterIds = follows.stream().map(Follow::getFollowerId).toList();
        Map<Long, User> userMap = userRepository.findAllById(requesterIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return follows.stream()
                .filter(f -> userMap.containsKey(f.getFollowerId())) // 탈퇴한 유저의 요청 제외
                .map(f -> {
            User user = userMap.get(f.getFollowerId());
            return new FriendRequestResponse(
                    user.getId(),
                    user.getNickname(),
                    user.getProfileImageUrl(),
                    user.getProfileUrl(),
                    f.getCreatedAt()
            );
        }).toList();
    }

    @Transactional
    public void acceptRequest(Long myId, Long requesterId) {
        Follow follow = followRepository.findByFollowerIdAndFollowingId(requesterId, myId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        if (follow.getStatus() != FollowStatus.PENDING) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        follow.accept();

        // 양방향 친구 관계 생성: 나 → 상대방 방향도 ACCEPTED로 생성
        var reverseOpt = followRepository.findByFollowerIdAndFollowingId(myId, requesterId);
        if (reverseOpt.isPresent()) {
            reverseOpt.get().accept();
        } else {
            Follow reverse = Follow.builder()
                    .followerId(myId)
                    .followingId(requesterId)
                    .status(FollowStatus.ACCEPTED)
                    .build();
            followRepository.save(reverse);
        }

        // 요청 보낸 사람에게 수락 알림 (탈퇴한 유저면 생략)
        User me = userRepository.findById(myId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        userRepository.findById(requesterId).ifPresent(requester ->
                sendFriendNotification(requester, NotificationType.FRIEND_ACCEPT,
                        "친구 요청이 수락됐어요",
                        me.getNickname() + "님이 친구 요청을 수락했어요"));
    }

    // 친구 알림은 장소/리뷰와 무관하므로 place_id와 review_id가 없다
    private void sendFriendNotification(User target, NotificationType type, String title, String body) {
        notificationService.send(target, type, null, null, title, body);
    }

    @Transactional
    public void rejectRequest(Long myId, Long requesterId) {
        Follow follow = followRepository.findByFollowerIdAndFollowingId(requesterId, myId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        if (follow.getStatus() != FollowStatus.PENDING) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        follow.reject();
    }

    @Transactional
    public void cancelFollow(Long followerId, Long followingId) {
        Follow follow = followRepository.findByFollowerIdAndFollowingId(followerId, followingId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        followRepository.delete(follow);

        // 양방향 관계도 삭제 (상대방이 나에게 보낸 별개의 PENDING 요청은 유지)
        followRepository.findByFollowerIdAndFollowingId(followingId, followerId)
                .filter(reverse -> reverse.getStatus() == FollowStatus.ACCEPTED)
                .ifPresent(followRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<UserSearchResponse> searchByNickname(Long userId, String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return List.of();
        }

        Pageable limit = PageRequest.of(0, SEARCH_LIMIT);
        List<User> users;
        if (ChosungUtil.hasChosung(nickname)) {
            String pattern = ChosungUtil.toRegexPattern(nickname);
            users = userRepository.findByNicknameRegex(pattern, limit);
        } else {
            users = userRepository.findByNicknameContaining(nickname, limit);
        }

        // 자기 자신 제외
        users = users.stream().filter(u -> !u.getId().equals(userId)).toList();

        if (users.isEmpty()) {
            return List.of();
        }

        List<Long> targetIds = users.stream().map(User::getId).toList();
        List<Follow> follows = followRepository.findByFollowerIdAndFollowingIdIn(userId, targetIds);
        Map<Long, FollowStatus> statusMap = follows.stream()
                .collect(Collectors.toMap(Follow::getFollowingId, Follow::getStatus));

        return users.stream().map(u -> {
            // REJECTED는 거절 사실을 숨기고 재요청 가능하도록 NONE으로 노출
            FollowStatus status = statusMap.get(u.getId());
            String relationStatus = (status == null || status == FollowStatus.REJECTED)
                    ? "NONE" : status.name();
            return new UserSearchResponse(
                    u.getId(),
                    u.getNickname(),
                    u.getProfileImageUrl(),
                    u.getProfileUrl(),
                    relationStatus
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> getFriends(Long userId) {
        List<Follow> follows = followRepository.findByFollowerIdAndStatus(userId, FollowStatus.ACCEPTED);

        List<Long> friendIds = follows.stream().map(Follow::getFollowingId).toList();
        Map<Long, User> userMap = userRepository.findAllById(friendIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return follows.stream()
                .filter(f -> userMap.containsKey(f.getFollowingId()))
                .map(f -> {
                    User user = userMap.get(f.getFollowingId());
                    return new FriendResponse(user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getProfileUrl());
                }).toList();
    }

    /**
     * 친구 최근 리뷰 피드
     * 수락된 친구들이 72h 이내 작성한 리뷰를 최신순으로 반환한다.
     * 이미 친구인 사람의 리뷰만 보므로 친구 공개(FRIENDS) 리뷰도 포함한다.
     */
    @Transactional(readOnly = true)
    public FriendFeedResponse getFriendFeed(Long userId, int limit) {
        int size = Math.max(1, Math.min(limit, FEED_MAX_LIMIT));

        List<Long> friendIds = followRepository.findByFollowerIdAndStatus(userId, FollowStatus.ACCEPTED)
                .stream().map(Follow::getFollowingId).toList();
        if (friendIds.isEmpty()) {
            return new FriendFeedResponse(List.of());
        }

        // 탈퇴 유저는 @SQLRestriction으로 걸러지므로, 조회 전에 살아 있는 친구만 남긴다.
        // 리뷰를 가져온 뒤에 거르면 탈퇴 친구의 리뷰가 limit을 차지해 응답이 모자라진다.
        Map<Long, User> userMap = userRepository.findAllById(friendIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        if (userMap.isEmpty()) {
            return new FriendFeedResponse(List.of());
        }

        List<Review> reviews = reviewRepository.findFriendFeed(
                List.copyOf(userMap.keySet()),
                LocalDateTime.now().minusHours(72),
                PageRequest.of(0, size));
        if (reviews.isEmpty()) {
            return new FriendFeedResponse(List.of());
        }

        // 장소명을 한 번에 조회 (N+1 방지)
        Map<Long, Place> placeMap = placeRepository.findAllById(
                        reviews.stream().map(Review::getPlaceId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Place::getId, p -> p));

        List<FriendFeedResponse.ReviewItem> items = reviews.stream()
                .map(r -> {
                    User author = userMap.get(r.getUserId());
                    Place place = placeMap.get(r.getPlaceId());
                    return new FriendFeedResponse.ReviewItem(
                            r.getId(),
                            r.getPlaceId(),
                            place != null ? place.getName() : null,
                            r.getCongestionLevel(),
                            r.getComment(),
                            r.getImageUrl(),
                            r.getThumbnailUrl(),
                            // 피드의 사진 동그라미가 thumbnail_small_url로 그려진다.
                            // 썸네일이 없는 옛 이미지(original/ 폴더 밖 업로드)는 이 값이 null이라
                            // 사진이 있는데도 동그라미가 비므로 큰 썸네일 -> 원본 순으로 폴백한다
                            firstNonNull(r.getThumbnailSmallUrl(), r.getThumbnailUrl(), r.getImageUrl()),
                            new FriendFeedResponse.UserInfo(
                                    author.getId(), author.getNickname(), author.getProfileUrl()),
                            r.getCreatedAt()
                    );
                })
                .toList();

        return new FriendFeedResponse(items);
    }

    private static String firstNonNull(String... urls) {
        for (String url : urls) {
            if (url != null) {
                return url;
            }
        }
        return null;
    }
}
