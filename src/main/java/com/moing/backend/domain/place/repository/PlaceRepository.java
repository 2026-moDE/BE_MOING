package com.moing.backend.domain.place.repository;

import com.moing.backend.domain.place.entity.Place;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    /** 장소명으로 활성 장소를 조회한다 (네이버 검색 결과 매칭용) */
    Optional<Place> findByNameAndIsActiveTrue(String name);

    /** 전체 활성 장소를 조회한다 */
    List<Place> findAllByIsActiveTrue();

    /** 72h 이내 리뷰가 있는 전체 활성 장소를 조회한다 */
    @Query(value = """
            SELECT * FROM places p
            WHERE p.is_active = true
              AND EXISTS (
                SELECT 1 FROM reviews r
                WHERE r.place_id = p.id
                  AND r.created_at > :since
              )
            """, nativeQuery = true)
    List<Place> findAllWithRecentReviews(@Param("since") LocalDateTime since);

    /** 72h 이전 리뷰만 있는 전체 활성 장소를 조회한다 */
    @Query(value = """
            SELECT * FROM places p
            WHERE p.is_active = true
              AND EXISTS (
                SELECT 1 FROM reviews r
                WHERE r.place_id = p.id
                  AND r.created_at <= :since
              )
              AND NOT EXISTS (
                SELECT 1 FROM reviews r
                WHERE r.place_id = p.id
                  AND r.created_at > :since
              )
            """, nativeQuery = true)
    List<Place> findAllWithArchivedReviews(@Param("since") LocalDateTime since);

    /** 반경 내 전체 활성 장소 (거리 오름차순) */
    @Query(value = """
            SELECT * FROM places p
            WHERE p.is_active = true
              AND (6371000 * acos(
                    GREATEST(-1.0, LEAST(1.0,
                      cos(radians(:lat)) * cos(radians(p.latitude))
                      * cos(radians(p.longitude) - radians(:lng))
                      + sin(radians(:lat)) * sin(radians(p.latitude))
                    ))
                  )) <= :radius
            ORDER BY (6371000 * acos(
                    GREATEST(-1.0, LEAST(1.0,
                      cos(radians(:lat)) * cos(radians(p.latitude))
                      * cos(radians(p.longitude) - radians(:lng))
                      + sin(radians(:lat)) * sin(radians(p.latitude))
                    ))
                  )) ASC
            """, nativeQuery = true)
    List<Place> findNearbyAll(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radius") int radius);

    /** 반경 내 72h 이내 리뷰가 있는 활성 장소 (거리 오름차순) */
    @Query(value = """
            SELECT * FROM places p
            WHERE p.is_active = true
              AND EXISTS (
                SELECT 1 FROM reviews r
                WHERE r.place_id = p.id
                  AND r.created_at > :since
              )
              AND (6371000 * acos(
                    GREATEST(-1.0, LEAST(1.0,
                      cos(radians(:lat)) * cos(radians(p.latitude))
                      * cos(radians(p.longitude) - radians(:lng))
                      + sin(radians(:lat)) * sin(radians(p.latitude))
                    ))
                  )) <= :radius
            ORDER BY (6371000 * acos(
                    GREATEST(-1.0, LEAST(1.0,
                      cos(radians(:lat)) * cos(radians(p.latitude))
                      * cos(radians(p.longitude) - radians(:lng))
                      + sin(radians(:lat)) * sin(radians(p.latitude))
                    ))
                  )) ASC
            """, nativeQuery = true)
    List<Place> findNearby(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radius") int radius,
            @Param("since") LocalDateTime since);

    /** 반경 내 72h 이전 리뷰만 있는 활성 장소 (거리 오름차순) */
    @Query(value = """
            SELECT * FROM places p
            WHERE p.is_active = true
              AND EXISTS (
                SELECT 1 FROM reviews r
                WHERE r.place_id = p.id
                  AND r.created_at <= :since
              )
              AND NOT EXISTS (
                SELECT 1 FROM reviews r
                WHERE r.place_id = p.id
                  AND r.created_at > :since
              )
              AND (6371000 * acos(
                    GREATEST(-1.0, LEAST(1.0,
                      cos(radians(:lat)) * cos(radians(p.latitude))
                      * cos(radians(p.longitude) - radians(:lng))
                      + sin(radians(:lat)) * sin(radians(p.latitude))
                    ))
                  )) <= :radius
            ORDER BY (6371000 * acos(
                    GREATEST(-1.0, LEAST(1.0,
                      cos(radians(:lat)) * cos(radians(p.latitude))
                      * cos(radians(p.longitude) - radians(:lng))
                      + sin(radians(:lat)) * sin(radians(p.latitude))
                    ))
                  )) ASC
            """, nativeQuery = true)
    List<Place> findNearbyWithArchivedReviews(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radius") int radius,
            @Param("since") LocalDateTime since);

    /** keyword가 name 또는 address에 포함된 활성 장소를 조회한다 */
    @Query("SELECT p FROM Place p WHERE p.isActive = true AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.address) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Place> searchByNameOrAddress(@Param("keyword") String keyword);

    /** quick_tag 검색 결과로 얻은 placeId 목록에 해당하는 활성 장소를 조회한다 */
    List<Place> findByIdInAndIsActiveTrue(List<Long> ids);

    /** 장소명 목록으로 활성 장소를 일괄 조회한다 (자동완성 N+1 방지) */
    List<Place> findByNameInAndIsActiveTrue(List<String> names);

    /** 활성 장소 수 (관리자 통계) */
    long countByIsActiveTrue();

    /** 관리자 장소 목록 (review_count 포함, keyword 검색, N+1 방지) */
    @Query(value = """
            SELECT p.id, p.name, p.address, p.category,
                   COUNT(r.id) AS review_count, p.is_active, p.created_at
            FROM places p
            LEFT JOIN reviews r ON r.place_id = p.id
            WHERE (:cursor IS NULL OR p.id < :cursor)
              AND (:keyword IS NULL
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(p.address) LIKE LOWER(CONCAT('%', :keyword, '%')))
            GROUP BY p.id
            ORDER BY p.id DESC
            """, nativeQuery = true)
    List<Object[]> findAdminPlaces(@Param("keyword") String keyword,
                                   @Param("cursor") Long cursor,
                                   Pageable pageable);
}
