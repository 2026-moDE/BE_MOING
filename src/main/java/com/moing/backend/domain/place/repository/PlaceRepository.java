package com.moing.backend.domain.place.repository;

import com.moing.backend.domain.place.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    /** 장소명으로 활성 장소를 조회한다 (네이버 검색 결과 매칭용) */
    Optional<Place> findByNameAndIsActiveTrue(String name);

    /**
     * Haversine 공식으로 반경(미터) 내 활성 장소 중 72h 이내 리뷰가 있는 장소를 거리 오름차순으로 조회한다.
     * 6371000 = 지구 반지름(m)
     */
    @Query(value = """
            SELECT *
            FROM places p
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

    /** keyword가 name 또는 address에 포함된 활성 장소를 조회한다 */
    @Query("SELECT p FROM Place p WHERE p.isActive = true AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.address) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Place> searchByNameOrAddress(@Param("keyword") String keyword);

    /** quick_tag 검색 결과로 얻은 placeId 목록에 해당하는 활성 장소를 조회한다 */
    List<Place> findByIdInAndIsActiveTrue(List<Long> ids);

    /** 장소명 목록으로 활성 장소를 일괄 조회한다 (자동완성 N+1 방지) */
    List<Place> findByNameInAndIsActiveTrue(List<String> names);
}
