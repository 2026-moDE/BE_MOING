package com.moing.backend.domain.piece.repository;

import com.moing.backend.domain.piece.entity.Piece;
import com.moing.backend.domain.piece.entity.PieceVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PieceRepository extends JpaRepository<Piece, Long> {

    // 리뷰당 조각은 하나만 만들 수 있다
    boolean existsByReviewId(Long reviewId);

    List<Piece> findByUserId(Long userId);

    List<Piece> findByUserIdAndVisibility(Long userId, PieceVisibility visibility);

    long countByUserId(Long userId);

    // 리뷰 삭제 시 딸린 조각 정리 (건별로 엔티티를 올리지 않도록 벌크로 지운다)
    @Modifying
    @Query("DELETE FROM Piece p WHERE p.reviewId = :reviewId")
    int deleteAllByReviewId(@Param("reviewId") Long reviewId);
}
