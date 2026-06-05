package com.moing.backend.domain.place.repository;

import com.moing.backend.domain.place.entity.PlaceCongestionCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface PlaceCongestionCacheRepository extends JpaRepository<PlaceCongestionCache, Long> {

    // 리뷰가 없어진 장소의 캐시 정리
    @Modifying
    @Query("DELETE FROM PlaceCongestionCache c WHERE c.placeId NOT IN :placeIds")
    void deleteAllByPlaceIdNotIn(@Param("placeIds") Set<Long> placeIds);
}
