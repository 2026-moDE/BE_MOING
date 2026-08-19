package com.moing.backend.domain.reaction.repository;

import com.moing.backend.domain.reaction.entity.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    // 리뷰당 한 사람의 반응은 최대 하나
    Optional<Reaction> findByReviewIdAndUserId(Long reviewId, Long userId);

    // 삭제된 건수를 돌려주므로 0이면 취소할 반응이 없었다는 뜻이다
    long deleteByReviewIdAndUserIdAndEmoji(Long reviewId, Long userId, String emoji);

    // 반응 목록 (먼저 누른 순)
    List<Reaction> findByReviewIdOrderByIdAsc(Long reviewId);
}
