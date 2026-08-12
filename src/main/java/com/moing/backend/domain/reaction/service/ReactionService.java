package com.moing.backend.domain.reaction.service;

import com.moing.backend.domain.follow.entity.FollowStatus;
import com.moing.backend.domain.follow.repository.FollowRepository;
import com.moing.backend.domain.reaction.dto.EmojiCount;
import com.moing.backend.domain.reaction.dto.ReactionCreateRequest;
import com.moing.backend.domain.reaction.dto.ReactionListResponse;
import com.moing.backend.domain.reaction.entity.Reaction;
import com.moing.backend.domain.reaction.repository.ReactionRepository;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.entity.Visibility;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final ReviewRepository reviewRepository;
    private final FollowRepository followRepository;

    // 이모지 반응 추가 (리뷰당 하나만 가능, 다른 이모지를 누르면 교체)
    @Transactional
    public void addReaction(Long userId, Long reviewId, ReactionCreateRequest request) {
        Review review = getReview(reviewId);
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

    // 이모지 반응 목록 조회 (이모지 종류별 그룹화)
    @Transactional(readOnly = true)
    public ReactionListResponse getReactions(Long userId, Long reviewId) {
        Review review = getReview(reviewId);
        validateAccess(userId, review);

        List<EmojiCount> counts = reactionRepository.countGroupedByEmoji(reviewId);
        if (counts.isEmpty()) {
            return new ReactionListResponse(List.of());
        }

        Set<String> myEmojis = reactionRepository.findEmojisByUser(reviewId, userId);

        List<ReactionListResponse.ReactionItem> items = counts.stream()
                .map(c -> new ReactionListResponse.ReactionItem(
                        c.emoji(),
                        c.count(),
                        myEmojis.contains(c.emoji())))
                .toList();

        return new ReactionListResponse(items);
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
