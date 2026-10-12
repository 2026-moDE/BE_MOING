package com.moing.backend.domain.congestion.repository;

import com.moing.backend.domain.congestion.entity.SeoulArea;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeoulAreaRepository extends JpaRepository<SeoulArea, Long> {

    /**
     * 반경 내에서 가장 가까운 지역 (거리 오름차순).
     *
     * <p>지역은 120여 곳의 넓은 단위라 기준 장소와 1:1로 대응하지 않는다.
     * 호출부가 Pageable로 1건만 받아 쓴다.
     */
    @Query(value = """
            SELECT * FROM seoul_areas a
            WHERE (6371000 * acos(
                    GREATEST(-1.0, LEAST(1.0,
                      cos(radians(:lat)) * cos(radians(a.latitude))
                      * cos(radians(a.longitude) - radians(:lng))
                      + sin(radians(:lat)) * sin(radians(a.latitude))
                    ))
                  )) <= :radius
            ORDER BY (6371000 * acos(
                    GREATEST(-1.0, LEAST(1.0,
                      cos(radians(:lat)) * cos(radians(a.latitude))
                      * cos(radians(a.longitude) - radians(:lng))
                      + sin(radians(:lat)) * sin(radians(a.latitude))
                    ))
                  )) ASC
            """, nativeQuery = true)
    List<SeoulArea> findNearest(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radius") int radius,
            Pageable pageable);

    /** 동기화에서 사라진 지역을 정리하기 위해 이름으로 조회한다 */
    List<SeoulArea> findByAreaNmNotIn(List<String> areaNms);

    /** 지역 선택 목록 (가나다순) */
    List<SeoulArea> findAllByOrderByAreaNmAsc();

    /** 지역 검색: 이름 부분 일치 (영문 지역명을 위해 대소문자 무시) */
    List<SeoulArea> findByAreaNmContainingIgnoreCaseOrderByAreaNmAsc(String keyword);

    /**
     * 지역 초성 검색 (PostgreSQL 정규식).
     * 패턴은 {@code ChosungUtil.toRegexPattern}이 만든다.
     */
    @Query(value = "SELECT * FROM seoul_areas WHERE area_nm ~ :pattern ORDER BY area_nm ASC",
            nativeQuery = true)
    List<SeoulArea> findByAreaNmRegex(@Param("pattern") String pattern);
}
