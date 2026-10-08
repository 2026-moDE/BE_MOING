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

    // 인기 장소: 복수 장소의 72h 이내 노출 가능한 리뷰 (장소별 최신 순)
    // 리뷰 수·최신 리뷰·대표 사진을 이 한 번의 조회로 모두 뽑는다.
    // 조건은 PlaceRepository.findHotPlaces의 카운트 조건과 반드시 같아야 한다.
    @Query("""
            SELECT r FROM Review r
            WHERE r.placeId IN :placeIds
              AND r.createdAt > :since
              AND r.isBlinded = false
              AND (r.visibility IS NULL
                   OR r.visibility <> com.moing.backend.domain.review.entity.Visibility.FRIENDS)
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<Review> findVisibleRecentReviewsByPlaceIds(
            @Param("placeIds") List<Long> placeIds,
            @Param("since") LocalDateTime since);

    // 친구 피드: 친구들이 72h 이내 쓴 리뷰 (최신순)
    // 이미 수락된 친구만 대상이므로 친구 공개(FRIENDS) 리뷰도 걸러내지 않는다
    @Query("""
            SELECT r FROM Review r
            WHERE r.userId IN :userIds
              AND r.createdAt > :since
              AND r.isBlinded = false
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<Review> findFriendFeed(
            @Param("userIds") List<Long> userIds,
            @Param("since") LocalDateTime since,
            Pageable pageable);

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

    /**
     * 탐색 탭: 좌표 반경 내 72h 이내 리뷰의 id와 거리(m).
     *
     * <p>노출 조건은 {@link #findCurrentReviews}와 같지만 기준이 장소가 아니라 좌표 반경이다.
     * 거리는 PlaceRepository의 반경 쿼리와 같은 하버사인 식을 쓴다. 좌표가 없는 옛 리뷰는
     * 식이 NULL이 되어 자연히 빠진다.
     *
     * <p>정렬은 seed를 섞은 해시라 같은 seed면 순서가 재현된다. 매 요청마다 새로 섞으면
     * 2페이지가 1페이지의 "다음"이라는 보장이 없어 본 사진이 또 나오거나 끝까지 안 나오는
     * 리뷰가 생긴다. 정렬 키가 해시여서 id 커서를 쓸 수 없으므로 offset으로 넘긴다
     * (72h 이내로 한정되어 전체 규모가 작다).
     *
     * <p>조건을 바꾸면 {@link #countExploreReviews}도 같이 고쳐야 total이 어긋나지 않는다.
     */
    @Query(value = """
            SELECT r.id,
                   (6371000 * acos(
                     GREATEST(-1.0, LEAST(1.0,
                       cos(radians(:lat)) * cos(radians(r.latitude))
                       * cos(radians(r.longitude) - radians(:lng))
                       + sin(radians(:lat)) * sin(radians(r.latitude))
                     ))
                   )) AS distance
            FROM reviews r
            WHERE r.created_at > :since
              AND r.is_blinded = false
              AND r.image_url IS NOT NULL
              AND (r.visibility IS NULL
                   OR r.visibility <> 'FRIENDS'
                   OR r.user_id = :viewerId
                   OR EXISTS (SELECT 1 FROM follows f
                              WHERE f.follower_id = :viewerId
                                AND f.following_id = r.user_id
                                AND f.status = 'ACCEPTED'))
              AND (6371000 * acos(
                    GREATEST(-1.0, LEAST(1.0,
                      cos(radians(:lat)) * cos(radians(r.latitude))
                      * cos(radians(r.longitude) - radians(:lng))
                      + sin(radians(:lat)) * sin(radians(r.latitude))
                    ))
                  )) <= :radius
            ORDER BY md5(CAST(r.id AS text) || CAST(:seed AS text))
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Object[]> findExploreReviewIds(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radius") int radius,
            @Param("since") LocalDateTime since,
            @Param("viewerId") Long viewerId,
            @Param("seed") String seed,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /** 탐색 탭 total. 조건은 {@link #findExploreReviewIds}와 반드시 같아야 한다 */
    @Query(value = """
            SELECT COUNT(*)
            FROM reviews r
            WHERE r.created_at > :since
              AND r.is_blinded = false
              AND r.image_url IS NOT NULL
              AND (r.visibility IS NULL
                   OR r.visibility <> 'FRIENDS'
                   OR r.user_id = :viewerId
                   OR EXISTS (SELECT 1 FROM follows f
                              WHERE f.follower_id = :viewerId
                                AND f.following_id = r.user_id
                                AND f.status = 'ACCEPTED'))
              AND (6371000 * acos(
                    GREATEST(-1.0, LEAST(1.0,
                      cos(radians(:lat)) * cos(radians(r.latitude))
                      * cos(radians(r.longitude) - radians(:lng))
                      + sin(radians(:lat)) * sin(radians(r.latitude))
                    ))
                  )) <= :radius
            """, nativeQuery = true)
    long countExploreReviews(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radius") int radius,
            @Param("since") LocalDateTime since,
            @Param("viewerId") Long viewerId);

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
