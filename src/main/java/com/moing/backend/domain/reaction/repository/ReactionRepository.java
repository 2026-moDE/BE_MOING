package com.moing.backend.domain.reaction.repository;

import com.moing.backend.domain.reaction.dto.EmojiCount;
import com.moing.backend.domain.reaction.entity.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    // 리뷰당 한 사람의 반응은 최대 하나
    Optional<Reaction> findByReviewIdAndUserId(Long reviewId, Long userId);

    // 삭제된 건수를 돌려주므로 0이면 취소할 반응이 없었다는 뜻이다
    long deleteByReviewIdAndUserIdAndEmoji(Long reviewId, Long userId, String emoji);

    // 이모지 종류별 집계 (많이 눌린 순, 동수면 먼저 눌린 이모지 순)
    @Query("""
            SELECT new com.moing.backend.domain.reaction.dto.EmojiCount(r.emoji, COUNT(r))
            FROM Reaction r
            WHERE r.reviewId = :reviewId
            GROUP BY r.emoji
            ORDER BY COUNT(r) DESC, MIN(r.id) ASC
            """)
    List<EmojiCount> countGroupedByEmoji(@Param("reviewId") Long reviewId);

    // is_mine 계산용 - 내가 이 리뷰에 남긴 이모지 목록
    @Query("SELECT r.emoji FROM Reaction r WHERE r.reviewId = :reviewId AND r.userId = :userId")
    Set<String> findEmojisByUser(@Param("reviewId") Long reviewId, @Param("userId") Long userId);
}
