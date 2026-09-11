package com.moing.backend.domain.congestion.service;

import com.moing.backend.domain.congestion.dto.CongestionResponse;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.review.entity.CongestionLevel;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.global.infra.SeoulCityDataResponse;
import com.moing.backend.global.infra.SeoulPublicDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 공공 데이터 기반 혼잡도 조회 테스트
 * 지역명은 공공 API의 조회 키라, 서울시 공식 장소명과 정확히 일치하는지도 함께 검증한다.
 */
class CongestionServiceTest {

    private PlaceRepository placeRepository;
    private ReviewRepository reviewRepository;
    private SeoulPublicDataService seoulPublicDataService;
    private CongestionService congestionService;

    @BeforeEach
    void setUp() {
        placeRepository = mock(PlaceRepository.class);
        reviewRepository = mock(ReviewRepository.class);
        seoulPublicDataService = mock(SeoulPublicDataService.class);
        congestionService = new CongestionService(placeRepository, reviewRepository, seoulPublicDataService);

        // 리뷰가 없는 상태 = 항상 공공 API 경로를 타도록
        when(placeRepository.findNearbyAll(anyDouble(), anyDouble(), anyInt())).thenReturn(List.of());
        when(reviewRepository.countByPlaceIdsAndCreatedAtAfter(anyList(), any())).thenReturn(0L);
    }

    @Test
    @DisplayName("하드코딩된 지역명이 모두 서울시 공식 장소명이다")
    void 지역명이_공식_장소명과_일치한다() throws IOException {
        Set<String> official = officialAreaNames();

        assertThat(configuredAreaNames()).allSatisfy(name ->
                assertThat(official).as("서울시 공식 장소명에 없는 지역명: %s", name).contains(name));
    }

    @ParameterizedTest
    @CsvSource({
            "여유, RELAXED",
            "보통, MODERATE",
            "'약간 붐빔', CROWDED",
            "붐빔, VERY_CROWDED"
    })
    @DisplayName("공공 API 혼잡도 문자열을 CongestionLevel로 매핑한다")
    void 공공_혼잡도를_매핑한다(String publicLevel, CongestionLevel expected) {
        when(seoulPublicDataService.fetchPopulation(anyString()))
                .thenReturn(new SeoulCityDataResponse.Row("서울역", publicLevel, 18000, 20000));

        CongestionResponse.AreaItem item = firstAreaNear(37.55659, 126.97303);

        assertThat(item.congestionLevel()).isEqualTo(expected);
        assertThat(item.congestionLabel()).isEqualTo(expected.getLabel());
        assertThat(item.bubbleColor()).isEqualTo(expected.getBubbleColor());
    }

    @Test
    @DisplayName("공공 API 인구수는 min·max의 평균으로 내려준다")
    void 인구수는_평균값이다() {
        when(seoulPublicDataService.fetchPopulation(anyString()))
                .thenReturn(new SeoulCityDataResponse.Row("서울역", "여유", 18000, 20000));

        assertThat(firstAreaNear(37.55659, 126.97303).population()).isEqualTo(19000);
        assertThat(firstAreaNear(37.55659, 126.97303).source()).isEqualTo("PUBLIC");
    }

    @Test
    @DisplayName("공공 API가 데이터를 못 주면 해당 지역은 응답에서 제외한다")
    void 공공데이터가_없으면_제외된다() {
        when(seoulPublicDataService.fetchPopulation(anyString())).thenReturn(null);

        assertThat(congestionService.getNearbyCongestion(37.55659, 126.97303, 500).areas()).isEmpty();
    }

    @Test
    @DisplayName("반경 밖 지역은 조회하지 않는다")
    void 반경_밖_지역은_제외된다() {
        when(seoulPublicDataService.fetchPopulation(anyString()))
                .thenReturn(new SeoulCityDataResponse.Row("서울역", "여유", 18000, 20000));

        // 서울역 기준 500m 안에 다른 주요 지역은 없다
        assertThat(congestionService.getNearbyCongestion(37.55659, 126.97303, 500).areas())
                .extracting(CongestionResponse.AreaItem::name)
                .containsExactly("서울역");
    }

    private CongestionResponse.AreaItem firstAreaNear(double latitude, double longitude) {
        List<CongestionResponse.AreaItem> areas =
                congestionService.getNearbyCongestion(latitude, longitude, 500).areas();
        assertThat(areas).isNotEmpty();
        return areas.get(0);
    }

    @SuppressWarnings("unchecked")
    private List<String> configuredAreaNames() {
        List<Object> areas = (List<Object>) ReflectionTestUtils.getField(CongestionService.class, "SEOUL_AREAS");
        assertThat(areas).isNotNull().isNotEmpty();
        return areas.stream()
                .map(area -> String.valueOf(ReflectionTestUtils.invokeGetterMethod(area, "name")))
                .toList();
    }

    private Set<String> officialAreaNames() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixture/seoul-official-area-names.txt")) {
            assertThat(in).as("공식 장소명 픽스처").isNotNull();
            return Arrays.stream(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toSet());
        }
    }
}
