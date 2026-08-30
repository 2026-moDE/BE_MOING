package com.moing.backend.domain.reaction.service;

import com.moing.backend.domain.follow.entity.FollowStatus;
import com.moing.backend.domain.follow.repository.FollowRepository;
import com.moing.backend.domain.notification.entity.NotificationType;
import com.moing.backend.domain.notification.service.NotificationService;
import com.moing.backend.domain.place.entity.Place;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.reaction.dto.ReactionCreateRequest;
import com.moing.backend.domain.reaction.dto.ReactionListResponse;
import com.moing.backend.domain.reaction.entity.Reaction;
import com.moing.backend.domain.reaction.repository.ReactionRepository;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.entity.Visibility;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.domain.user.entity.User;
import com.moing.backend.domain.user.repository.UserRepository;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final ReviewRepository reviewRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final PlaceRepository placeRepository;
    private final NotificationService notificationService;

    // 이모지 반응 추가 (리뷰당 하나만 가능, 다른 이모지를 누르면 교체)
    // 자기 리뷰에는 남길 수 없고 목록 조회만 가능하다
    @Transactional
    public void addReaction(Long userId, Long reviewId, ReactionCreateRequest request) {
        Review review = getReview(reviewId);
        if (userId.equals(review.getUserId())) {
            throw new CustomException(ErrorCode.SELF_REACTION_NOT_ALLOWED);
        }
        validateAccess(userId, review);

        String emoji = request.emoji();

        Optional<Reaction> existing = reactionRepository.findByReviewIdAndUserId(reviewId, userId);
        if (existing.isPresent()) {
            Reaction reaction = existing.get();
            // 같은 이모지를 다시 누른 경우
            if (reaction.getEmoji().equals(emoji)) {
                throw new CustomException(ErrorCode.DUPLICATE_REACTION);
            }
            reaction.changeEmoji(emoji);
            // 다른 이모지로 바꾼 것도 새 반응으로 보고 알린다
            notifyReviewAuthor(review);
            return;
        }

        try {
            // 동시 요청으로 이미 반응이 생겼다면 unique constraint 위반을 409로 변환
            reactionRepository.saveAndFlush(Reaction.builder()
                    .reviewId(reviewId)
                    .userId(userId)
                    .emoji(emoji)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.DUPLICATE_REACTION);
        }

        notifyReviewAuthor(review);
    }

    // 리뷰 작성자에게 반응 알림 (자기 리뷰에는 반응할 수 없으므로 본인에게 갈 일은 없다)
    private void notifyReviewAuthor(Review review) {
        String placeName = placeRepository.findById(review.getPlaceId())
                .map(Place::getName)
                .orElse(null);
        if (placeName == null) return;

        notificationService.send(
                review.getUserId(),
                NotificationType.REACTION,
                review.getPlaceId(),
                review.getId(),
                "작성하신 리뷰에 새로운 반응이 달렸어요",
                "'" + placeName + "'에 작성하신 리뷰에 새로운 반응이 달렸어요.");
    }

    // 이모지 반응 취소 (본인이 남긴 반응만)
    // 친구를 끊은 뒤에도 자기 반응은 지울 수 있어야 하므로 공개 범위는 확인하지 않는다
    @Transactional
    public void deleteReaction(Long userId, Long reviewId, String emoji) {
        if (emoji == null || emoji.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        getReview(reviewId);

        if (reactionRepository.deleteByReviewIdAndUserIdAndEmoji(reviewId, userId, emoji) == 0) {
            throw new CustomException(ErrorCode.REACTION_NOT_FOUND);
        }
    }

    // 이모지 반응 목록 조회 (반응을 남긴 사람 정보 포함)
    @Transactional(readOnly = true)
    public ReactionListResponse getReactions(Long userId, Long reviewId) {
        Review review = getReview(reviewId);
        validateAccess(userId, review);

        List<Reaction> reactions = reactionRepository.findByReviewIdOrderByIdAsc(reviewId);
        if (reactions.isEmpty()) {
            return new ReactionListResponse(List.of());
        }

        // 반응을 남긴 유저를 한 번에 조회 (N+1 방지)
        List<Long> userIds = reactions.stream().map(Reaction::getUserId).distinct().toList();
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<ReactionListResponse.ReactionItem> items = reactions.stream()
                .map(r -> new ReactionListResponse.ReactionItem(
                        r.getEmoji(),
                        userId.equals(r.getUserId()),
                        toUserInfo(userMap.get(r.getUserId()))))
                .toList();

        return new ReactionListResponse(items);
    }

    // 탈퇴한 유저의 반응도 남아 있으므로 작성자를 찾지 못하는 경우를 대비한다
    private ReactionListResponse.UserInfo toUserInfo(User user) {
        return user != null
                ? new ReactionListResponse.UserInfo(user.getNickname(), user.getProfileImageUrl(), user.getProfileUrl())
                : new ReactionListResponse.UserInfo("알 수 없음", null, null);
    }

    // 친구 공개 리뷰는 리뷰 작성자 본인과 친구만 접근할 수 있다
    private void validateAccess(Long userId, Review review) {
        if (review.getVisibility() != Visibility.FRIENDS || userId.equals(review.getUserId())) {
            return;
        }

        boolean isFriend = followRepository.existsByFollowerIdAndFollowingIdAndStatusIn(
                userId, review.getUserId(), List.of(FollowStatus.ACCEPTED));
        if (!isFriend) {
            throw new CustomException(ErrorCode.NOT_FRIEND_REVIEW);
        }
    }

    private Review getReview(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
    }
}
