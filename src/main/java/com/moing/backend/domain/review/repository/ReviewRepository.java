package com.moing.backend.domain.review.repository;

import com.moing.backend.domain.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    // 72h 이내 가장 최근 리뷰 (혼잡도)
    Optional<Review> findTopByPlaceIdAndCreatedAtAfterOrderByCreatedAtDesc(
            Long placeId, LocalDateTime since);

    // 72h 이내 helpful_count 가장 높은 리뷰 (대표 사진)
    @Query("""
            SELECT r FROM Review r
            WHERE r.placeId = :placeId
              AND r.createdAt > :since
              AND r.imageUrl IS NOT NULL
            ORDER BY r.helpfulCount DESC, r.createdAt DESC
            """)
    Optional<Review> findTopWithImageByPlaceId(
            @Param("placeId") Long placeId,
            @Param("since") LocalDateTime since);
}
