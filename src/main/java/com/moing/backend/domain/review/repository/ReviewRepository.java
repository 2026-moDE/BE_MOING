package com.moing.backend.domain.review.repository;

import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.entity.Visibility;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    // 72h 이내 가장 최근 리뷰 (혼잡도)
    Optional<Review> findTopByPlaceIdAndIsBlindedFalseAndCreatedAtAfterOrderByCreatedAtDesc(
            Long placeId, LocalDateTime since);

    // 72h 이내 가장 최근 리뷰 (대표 사진, 친구 공개 리뷰 제외)
    @Query("""
            SELECT r FROM Review r
            WHERE r.placeId = :placeId
              AND r.createdAt > :since
              AND r.imageUrl IS NOT NULL
              AND r.isBlinded = false
              AND (r.visibility IS NULL
                   OR r.visibility <> com.moing.backend.domain.review.entity.Visibility.FRIENDS)
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<Review> findTopWithImageByPlaceId(
            @Param("placeId") Long placeId,
            @Param("since") LocalDateTime since,
            Pageable pageable);

    // 시간 제한 없이 가장 최근 리뷰 (대표 사진, 전체 필터용, 친구 공개 리뷰 제외)
    @Query("""
            SELECT r FROM Review r
            WHERE r.placeId = :placeId
              AND r.imageUrl IS NOT NULL
              AND r.isBlinded = false
              AND (r.visibility IS NULL
                   OR r.visibility <> com.moing.backend.domain.review.entity.Visibility.FRIENDS)
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<Review> findTopWithImageAllTimeByPlaceId(
            @Param("placeId") Long placeId,
            Pageable pageable);

    // 72h 이전 리뷰 중 가장 최근 리뷰 (대표 사진, 과거 필터용, 친구 공개 리뷰 제외)
    @Query("""
            SELECT r FROM Review r
            WHERE r.placeId = :placeId
              AND r.createdAt <= :since
              AND r.imageUrl IS NOT NULL
              AND r.isBlinded = false
              AND (r.visibility IS NULL
                   OR r.visibility <> com.moing.backend.domain.review.entity.Visibility.FRIENDS)
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<Review> findTopWithArchivedImageByPlaceId(
            @Param("placeId") Long placeId,
            @Param("since") LocalDateTime since,
            Pageable pageable);

    // 72h 이내 리뷰 수 (상세 조회용)
    // 조회자에게 실제로 보이는 리뷰만 센다. 조건은 findCurrentReviews와 동일해야
    // 목록에 뜨는 개수와 상세의 review_count가 어긋나지 않는다.
    @Query("""
            SELECT COUNT(r) FROM Review r
            WHERE r.placeId = :placeId
              AND r.createdAt > :since
              AND r.isBlinded = false
              AND (r.visibility IS NULL
                   OR r.visibility <> com.moing.backend.domain.review.entity.Visibility.FRIENDS
                   OR r.userId = :viewerId
                   OR EXISTS (SELECT f.id FROM Follow f
                              WHERE f.followerId = :viewerId
                                AND f.followingId = r.userId
                                AND f.status = com.moing.backend.domain.follow.entity.FollowStatus.ACCEPTED))
            """)
    long countVisibleCurrentReviews(
            @Param("placeId") Long placeId,
            @Param("since") LocalDateTime since,
            @Param("viewerId") Long viewerId);

    // 72h 이내 커서 기반 조회 (현재 리뷰, 친구 공개 리뷰는 작성자 본인과 친구에게만)
    @Query("""
            SELECT r FROM Review r
            WHERE r.placeId = :placeId
              AND r.createdAt > :since
              AND r.isBlinded = false
              AND (:cursor IS NULL OR r.id < :cursor)
              AND (r.visibility IS NULL
                   OR r.visibility <> com.moing.backend.domain.review.entity.Visibility.FRIENDS
                   OR r.userId = :viewerId
                   OR EXISTS (SELECT f.id FROM Follow f
                              WHERE f.followerId = :viewerId
                                AND f.followingId = r.userId
                                AND f.status = com.moing.backend.domain.follow.entity.FollowStatus.ACCEPTED))
            ORDER BY r.id DESC
            """)
    List<Review> findCurrentReviews(
            @Param("placeId") Long placeId,
            @Param("since") LocalDateTime since,
            @Param("cursor") Long cursor,
            @Param("viewerId") Long viewerId,
            Pageable pageable);

    // 72h 경과 커서 기반 조회 (과거 리뷰, 친구 공개 리뷰는 작성자 본인과 친구에게만)
    @Query("""
            SELECT r FROM Review r
            WHERE r.placeId = :placeId
              AND r.createdAt <= :since
              AND r.isBlinded = false
              AND (:cursor IS NULL OR r.id < :cursor)
              AND (r.visibility IS NULL
                   OR r.visibility <> com.moing.backend.domain.review.entity.Visibility.FRIENDS
                   OR r.userId = :viewerId
                   OR EXISTS (SELECT f.id FROM Follow f
                              WHERE f.followerId = :viewerId
                                AND f.followingId = r.userId
                                AND f.status = com.moing.backend.domain.follow.entity.FollowStatus.ACCEPTED))
            ORDER BY r.id DESC
            """)
    List<Review> findArchivedReviews(
            @Param("placeId") Long placeId,
            @Param("since") LocalDateTime since,
            @Param("cursor") Long cursor,
            @Param("viewerId") Long viewerId,
            Pageable pageable);

    // 혼잡도 집계용: 복수 장소의 72h 이내 리뷰 수 조회
    @Query("SELECT COUNT(r) FROM Review r WHERE r.placeId IN :placeIds AND r.createdAt > :since AND r.isBlinded = false")
    long countByPlaceIdsAndCreatedAtAfter(@Param("placeIds") List<Long> placeIds, @Param("since") LocalDateTime since);

    // 혼잡도 집계용: 복수 장소의 72h 이내 리뷰 목록 조회
    @Query("SELECT r FROM Review r WHERE r.placeId IN :placeIds AND r.createdAt > :since AND r.isBlinded = false")
    List<Review> findByPlaceIdsAndCreatedAtAfter(@Param("placeIds") List<Long> placeIds, @Param("since") LocalDateTime since);

    // 마이페이지: 사용자 리뷰 수
    long countByUserId(Long userId);

    // 마이페이지: 사용자가 리뷰한 distinct 장소 수
    @Query("SELECT COUNT(DISTINCT r.placeId) FROM Review r WHERE r.userId = :userId")
    long countDistinctPlaceIdByUserId(@Param("userId") Long userId);

    // 구독 목록 썸네일: 복수 장소의 72h 이내 가장 최근 이미지 URL (친구 공개 리뷰 제외)
    @Query("""
            SELECT r FROM Review r
            WHERE r.placeId IN :placeIds
              AND r.createdAt > :since
              AND r.imageUrl IS NOT NULL
              AND r.isBlinded = false
              AND (r.visibility IS NULL
                   OR r.visibility <> com.moing.backend.domain.review.entity.Visibility.FRIENDS)
            ORDER BY r.createdAt DESC
            """)
    List<Review> findRecentReviewsWithImageByPlaceIds(
            @Param("placeIds") List<Long> placeIds,
            @Param("since") LocalDateTime since);

    // quick_tag로 장소 ID 검색
    @Query("SELECT DISTINCT r.placeId FROM Review r WHERE r.quickTag = :tag AND r.isBlinded = false")
    List<Long> findPlaceIdsByQuickTag(@Param("tag") String tag);

    // 혼잡도 캐시 배치용: 최근 N시간 ACTIVE 리뷰 전체 (placeId 순, 최신 순)
    @Query("""
            SELECT r FROM Review r
            WHERE r.createdAt > :since
              AND r.status = 'ACTIVE'
              AND r.isBlinded = false
            ORDER BY r.placeId ASC, r.createdAt DESC
            """)
    List<Review> findAllRecentActiveReviews(@Param("since") LocalDateTime since);

    // 단일 장소 즉시 캐시 갱신용: 최근 N시간 ACTIVE 리뷰 (최신 순)
    @Query("""
            SELECT r FROM Review r
            WHERE r.placeId = :placeId
              AND r.createdAt > :since
              AND r.status = 'ACTIVE'
              AND r.isBlinded = false
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

    // 타인 프로필/리뷰 목록: 조회자에게 보이는 리뷰만 커서 기반으로 조회한다.
    // visibility가 null인 옛 리뷰는 전체 공개로 취급한다(엔티티가 기본값을 넣기 전에 쌓인 것들).
    // 친구가 아니면 visibilities에 PUBLIC만 넘겨 친구 공개 리뷰를 걸러낸다.
    @Query("""
            SELECT r FROM Review r
            WHERE r.userId = :userId
              AND r.isBlinded = false
              AND (:cursor IS NULL OR r.id < :cursor)
              AND (r.visibility IS NULL OR r.visibility IN :visibilities)
            ORDER BY r.id DESC
            """)
    List<Review> findUserVisibleReviews(
            @Param("userId") Long userId,
            @Param("visibilities") List<Visibility> visibilities,
            @Param("cursor") Long cursor,
            Pageable pageable);

    // 타인 프로필의 review_count. 조건을 findUserVisibleReviews와 맞춰야
    // 목록에 뜨는 개수와 어긋나지 않는다
    @Query("""
            SELECT COUNT(r) FROM Review r
            WHERE r.userId = :userId
              AND r.isBlinded = false
              AND (r.visibility IS NULL OR r.visibility IN :visibilities)
            """)
    long countUserVisibleReviews(
            @Param("userId") Long userId,
            @Param("visibilities") List<Visibility> visibilities);

    // 오늘 리뷰 수 (관리자 통계)
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime start, LocalDateTime end);

    // 관리자 리뷰 목록 (place_name, author_nickname 조인, 블라인드 포함)
    @Query(value = """
            SELECT r.id, p.name AS place_name, r.image_url, r.congestion_level,
                   r.comment, u.nickname AS author_nickname,
                   r.is_blinded, r.status, r.created_at,
                   r.thumbnail_url, r.thumbnail_small_url
            FROM reviews r
            LEFT JOIN places p ON p.id = r.place_id
            LEFT JOIN users u ON u.id = r.user_id
            WHERE (:cursor IS NULL OR r.id < :cursor)
              AND (:filterStatus IS NULL
                   OR (:filterStatus = 'BLINDED' AND r.is_blinded = true)
                   OR (:filterStatus = 'ACTIVE' AND r.status = 'ACTIVE' AND r.is_blinded = false)
                   OR (:filterStatus = 'ARCHIVED' AND r.status = 'ARCHIVED' AND r.is_blinded = false))
            ORDER BY r.id DESC
            """, nativeQuery = true)
    List<Object[]> findAdminReviews(@Param("filterStatus") String filterStatus,
                                    @Param("cursor") Long cursor,
                                    Pageable pageable);
}
