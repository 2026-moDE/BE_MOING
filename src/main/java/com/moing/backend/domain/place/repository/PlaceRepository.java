package com.moing.backend.domain.place.repository;

import com.moing.backend.domain.place.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    /**
     * Haversine 공식으로 반경(미터) 내 활성 장소를 거리 오름차순으로 조회한다.
     * 6371000 = 지구 반지름(m)
     */
    @Query(value = """
            SELECT *
            FROM places p
            WHERE p.is_active = true
              AND (6371000 * acos(
                    LEAST(1.0,
                      cos(radians(:lat)) * cos(radians(p.latitude))
                      * cos(radians(p.longitude) - radians(:lng))
                      + sin(radians(:lat)) * sin(radians(p.latitude))
                    )
                  )) <= :radius
            ORDER BY (6371000 * acos(
                    LEAST(1.0,
                      cos(radians(:lat)) * cos(radians(p.latitude))
                      * cos(radians(p.longitude) - radians(:lng))
                      + sin(radians(:lat)) * sin(radians(p.latitude))
                    )
                  )) ASC
            """, nativeQuery = true)
    List<Place> findNearby(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radius") int radius);
}
