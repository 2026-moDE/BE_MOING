package com.moing.backend.domain.reaction.repository;

import com.moing.backend.domain.reaction.entity.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    // 리뷰당 한 사람의 반응은 최대 하나
    Optional<Reaction> findByReviewIdAndUserId(Long reviewId, Long userId);

    // 삭제된 건수를 돌려주므로 0이면 취소할 반응이 없었다는 뜻이다
    long deleteByReviewIdAndUserIdAndEmoji(Long reviewId, Long userId, String emoji);

    // 반응 목록 (먼저 누른 순)
    List<Reaction> findByReviewIdOrderByIdAsc(Long reviewId);

    // 리뷰 삭제 시 딸린 반응 정리 (건별로 엔티티를 올리지 않도록 벌크로 지운다)
    @Modifying
    @Query("DELETE FROM Reaction r WHERE r.reviewId = :reviewId")
    int deleteAllByReviewId(@Param("reviewId") Long reviewId);
}
