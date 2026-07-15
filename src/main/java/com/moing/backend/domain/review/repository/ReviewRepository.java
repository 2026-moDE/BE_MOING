package com.moing.backend.domain.review.repository;

import com.moing.backend.domain.review.entity.Review;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
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
    List<Review> findTopWithImageByPlaceId(
            @Param("placeId") Long placeId,
            @Param("since") LocalDateTime since,
            Pageable pageable);

    // 시간 제한 없이 helpful_count 가장 높은 리뷰 (대표 사진, 전체 필터용)
    @Query("""
            SELECT r FROM Review r
            WHERE r.placeId = :placeId
              AND r.imageUrl IS NOT NULL
            ORDER BY r.helpfulCount DESC, r.createdAt DESC
            """)
    List<Review> findTopWithImageAllTimeByPlaceId(
            @Param("placeId") Long placeId,
            Pageable pageable);

    // 72h 이전 리뷰 중 helpful_count 가장 높은 리뷰 (대표 사진, 과거 필터용)
    @Query("""
            SELECT r FROM Review r
            WHERE r.placeId = :placeId
              AND r.createdAt <= :since
              AND r.imageUrl IS NOT NULL
            ORDER BY r.helpfulCount DESC, r.createdAt DESC
            """)
    List<Review> findTopWithArchivedImageByPlaceId(
            @Param("placeId") Long placeId,
            @Param("since") LocalDateTime since,
            Pageable pageable);

    // 72h 이내 리뷰 수 (상세 조회용)
    long countByPlaceIdAndCreatedAtAfter(Long placeId, LocalDateTime since);

    // 72h 이내 커서 기반 조회 (현재 리뷰)
    @Query("""
            SELECT r FROM Review r
            WHERE r.placeId = :placeId
              AND r.createdAt > :since
              AND (:cursor IS NULL OR r.id < :cursor)
            ORDER BY r.id DESC
            """)
    List<Review> findCurrentReviews(
            @Param("placeId") Long placeId,
            @Param("since") LocalDateTime since,
            @Param("cursor") Long cursor,
            Pageable pageable);

    // 72h 경과 커서 기반 조회 (과거 리뷰)
    @Query("""
            SELECT r FROM Review r
            WHERE r.placeId = :placeId
              AND r.createdAt <= :since
              AND (:cursor IS NULL OR r.id < :cursor)
            ORDER BY r.id DESC
            """)
    List<Review> findArchivedReviews(
            @Param("placeId") Long placeId,
            @Param("since") LocalDateTime since,
            @Param("cursor") Long cursor,
            Pageable pageable);

    // 혼잡도 집계용: 복수 장소의 72h 이내 리뷰 수 조회
    @Query("SELECT COUNT(r) FROM Review r WHERE r.placeId IN :placeIds AND r.createdAt > :since")
    long countByPlaceIdsAndCreatedAtAfter(@Param("placeIds") List<Long> placeIds, @Param("since") LocalDateTime since);

    // 혼잡도 집계용: 복수 장소의 72h 이내 리뷰 목록 조회
    @Query("SELECT r FROM Review r WHERE r.placeId IN :placeIds AND r.createdAt > :since")
    List<Review> findByPlaceIdsAndCreatedAtAfter(@Param("placeIds") List<Long> placeIds, @Param("since") LocalDateTime since);

    // 마이페이지: 사용자 리뷰 수
    long countByUserId(Long userId);

    // 마이페이지: 사용자가 리뷰한 distinct 장소 수
    @Query("SELECT COUNT(DISTINCT r.placeId) FROM Review r WHERE r.userId = :userId")
    long countDistinctPlaceIdByUserId(@Param("userId") Long userId);

    // 구독 목록 썸네일: 복수 장소의 72h 이내 가장 최근 이미지 URL
    @Query("""
            SELECT r FROM Review r
            WHERE r.placeId IN :placeIds
              AND r.createdAt > :since
              AND r.imageUrl IS NOT NULL
            ORDER BY r.createdAt DESC
            """)
    List<Review> findRecentReviewsWithImageByPlaceIds(
            @Param("placeIds") List<Long> placeIds,
            @Param("since") LocalDateTime since);

    // quick_tag로 장소 ID 검색
    @Query("SELECT DISTINCT r.placeId FROM Review r WHERE r.quickTag = :tag")
    List<Long> findPlaceIdsByQuickTag(@Param("tag") String tag);

    // 혼잡도 캐시 배치용: 최근 N시간 ACTIVE 리뷰 전체 (placeId 순, 최신 순)
    @Query("""
            SELECT r FROM Review r
            WHERE r.createdAt > :since
              AND r.status = 'ACTIVE'
            ORDER BY r.placeId ASC, r.createdAt DESC
            """)
    List<Review> findAllRecentActiveReviews(@Param("since") LocalDateTime since);

    // 단일 장소 즉시 캐시 갱신용: 최근 N시간 ACTIVE 리뷰 (최신 순)
    @Query("""
            SELECT r FROM Review r
            WHERE r.placeId = :placeId
              AND r.createdAt > :since
              AND r.status = 'ACTIVE'
            ORDER BY r.createdAt DESC
            """)
    List<Review> findRecentActiveReviewsByPlaceId(
            @Param("placeId") Long placeId,
            @Param("since") LocalDateTime since,
            Pageable pageable);

    // 마이페이지: 내 리뷰 커서 기반 조회 (year/month 필터 포함)
    @Query("""
            SELECT r FROM Review r
            WHERE r.userId = :userId
              AND (:cursor IS NULL OR r.id < :cursor)
              AND (:year IS NULL OR :month IS NULL
                   OR (YEAR(r.createdAt) = :year AND MONTH(r.createdAt) = :month))
            ORDER BY r.id DESC
            """)
    List<Review> findMyReviews(
            @Param("userId") Long userId,
            @Param("cursor") Long cursor,
            @Param("year") Integer year,
            @Param("month") Integer month,
            Pageable pageable);
}
