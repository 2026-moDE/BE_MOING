package com.moing.backend.domain.congestion.repository;

import com.moing.backend.domain.congestion.entity.SeoulArea;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 최근접 지역 조회 네이티브 쿼리 테스트
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:seoularea;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class SeoulAreaRepositoryTest {

    private static final LocalDateTime SYNCED_AT = LocalDateTime.of(2026, 10, 10, 11, 15);

    // 성수카페거리와 서울숲공원은 약 1km 떨어져 있다
    private static final double SEONGSU_LAT = 37.54297;
    private static final double SEONGSU_LNG = 127.05660;

    @Autowired
    private SeoulAreaRepository seoulAreaRepository;

    @BeforeEach
    void setUp() {
        seoulAreaRepository.saveAll(List.of(
                SeoulArea.of("성수카페거리", "발달상권", SEONGSU_LAT, SEONGSU_LNG, SYNCED_AT),
                SeoulArea.of("서울숲공원", "공원", 37.54437, 127.04136, SYNCED_AT),
                SeoulArea.of("서울역", "인구밀집지역", 37.55659, 126.97303, SYNCED_AT)));
    }

    @Test
    @DisplayName("좌표에서 가장 가까운 지역 한 곳을 돌려준다")
    void 최근접_지역을_찾는다() {
        List<SeoulArea> found = seoulAreaRepository.findNearest(
                SEONGSU_LAT, SEONGSU_LNG, 2_000, PageRequest.of(0, 1));

        assertThat(found).extracting(SeoulArea::getAreaNm).containsExactly("성수카페거리");
    }

    @Test
    @DisplayName("가까운 순으로 정렬한다")
    void 거리순으로_정렬한다() {
        List<SeoulArea> found = seoulAreaRepository.findNearest(
                SEONGSU_LAT, SEONGSU_LNG, 20_000, PageRequest.of(0, 3));

        assertThat(found).extracting(SeoulArea::getAreaNm)
                .containsExactly("성수카페거리", "서울숲공원", "서울역");
    }

    @Test
    @DisplayName("반경을 넘는 지역은 제외한다")
    void 반경_밖은_제외한다() {
        // 제주도 좌표 — 서울 어느 지역과도 2km 안에 들어오지 않는다
        List<SeoulArea> found = seoulAreaRepository.findNearest(
                33.4996, 126.5312, 2_000, PageRequest.of(0, 1));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("동기화에서 빠진 지역을 이름으로 골라낸다")
    void 목록에_없는_지역을_찾는다() {
        List<SeoulArea> removed = seoulAreaRepository.findByAreaNmNotIn(List.of("성수카페거리", "서울역"));

        assertThat(removed).extracting(SeoulArea::getAreaNm).containsExactly("서울숲공원");
    }
}
