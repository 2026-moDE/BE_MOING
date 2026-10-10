package com.moing.backend.domain.congestion.service;

import com.moing.backend.domain.congestion.dto.CongestionResponse;
import com.moing.backend.domain.congestion.entity.SeoulArea;
import com.moing.backend.domain.congestion.repository.SeoulAreaRepository;
import com.moing.backend.domain.review.entity.CongestionLevel;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import com.moing.backend.global.infra.SeoulCityDataResponse;
import com.moing.backend.global.infra.SeoulPublicDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 서울시 도시데이터 기반 지역 혼잡도 조회 테스트
 */
class CongestionServiceTest {

    private static final double LATITUDE = 37.54297;
    private static final double LONGITUDE = 127.05660;
    private static final String AREA_NAME = "성수카페거리";
    // 서울시는 KST로 내려주고, SeoulCityDataResponse가 UTC로 바꿔 담는다
    private static final LocalDateTime SEOUL_TIME_KST = LocalDateTime.of(2026, 10, 10, 11, 15);
    private static final LocalDateTime EXPECTED_UTC = LocalDateTime.of(2026, 10, 10, 2, 15);
    private static final LocalDateTime FORECAST_START_KST = LocalDateTime.of(2026, 10, 10, 12, 0);

    private SeoulAreaRepository seoulAreaRepository;
    private SeoulPublicDataService seoulPublicDataService;
    private CongestionService congestionService;

    @BeforeEach
    void setUp() {
        seoulAreaRepository = mock(SeoulAreaRepository.class);
        seoulPublicDataService = mock(SeoulPublicDataService.class);
        congestionService = new CongestionService(seoulAreaRepository, seoulPublicDataService);

        when(seoulAreaRepository.findNearest(anyDouble(), anyDouble(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of(SeoulArea.of(AREA_NAME, "발달상권", LATITUDE, LONGITUDE, SEOUL_TIME_KST)));
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
        givenRow(row(publicLevel, List.of()));

        assertThat(nearby().congestionLevel()).isEqualTo(expected);
    }

    @Test
    @DisplayName("지역명·설명 문구·인구 상하한·기준 시각을 가공 없이 내려준다")
    void 응답_필드를_그대로_내려준다() {
        givenRow(row("붐빔", List.of()));

        CongestionResponse response = nearby();

        assertThat(response.areaName()).isEqualTo(AREA_NAME);
        assertThat(response.congestionMessage()).isEqualTo("사람이 많아 붐벼요.");
        // min·max를 평균으로 뭉개지 않고 둘 다 내려야 추이 띠에 범위를 그릴 수 있다
        assertThat(response.populationMin()).isEqualTo(16000);
        assertThat(response.populationMax()).isEqualTo(18000);
        // 호출 시각이 아니라 서울시 데이터 기준 시각이고, KST가 아니라 UTC로 내려간다
        assertThat(response.updatedAt()).isEqualTo(EXPECTED_UTC);
    }

    @Test
    @DisplayName("예측은 12시간치를 그대로 내려준다")
    void 예측을_전부_내려준다() {
        givenRow(row("여유", forecasts(12)));

        List<CongestionResponse.ForecastItem> forecast = nearby().forecast();

        assertThat(forecast).hasSize(12);
        // KST 12:00 → UTC 03:00
        assertThat(forecast.get(0).time()).isEqualTo(LocalDateTime.of(2026, 10, 10, 3, 0));
        assertThat(forecast.get(0).congestionLevel()).isEqualTo(CongestionLevel.CROWDED);
        assertThat(forecast.get(0).populationMin()).isEqualTo(28000);
        assertThat(forecast.get(0).populationMax()).isEqualTo(30000);
        assertThat(forecast.get(11).time()).isEqualTo(LocalDateTime.of(2026, 10, 10, 14, 0));
    }

    @Test
    @DisplayName("예측이 12건을 넘게 와도 12건까지만 내려준다")
    void 예측은_12건으로_자른다() {
        givenRow(row("여유", forecasts(24)));

        assertThat(nearby().forecast()).hasSize(12);
    }

    @Test
    @DisplayName("예측이 없는 지역은 빈 배열을 내려준다")
    void 예측이_없으면_빈_배열이다() {
        givenRow(new SeoulCityDataResponse.Row(
                AREA_NAME, "POI068", "여유", "여유로워요.", 4000, 6000, SEOUL_TIME_KST, "N", null));

        assertThat(nearby().forecast()).isEmpty();
    }

    @Test
    @DisplayName("반경 내 지역이 없으면 404로 끊는다")
    void 반경_내_지역이_없으면_404다() {
        when(seoulAreaRepository.findNearest(anyDouble(), anyDouble(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of());

        assertThatThrownBy(this::nearby)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.AREA_NOT_FOUND);
    }

    @Test
    @DisplayName("서울시 응답을 못 받으면 502로 알린다")
    void 서울시_응답이_없으면_502다() {
        givenRow(null);

        assertThatThrownBy(this::nearby)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }

    private CongestionResponse nearby() {
        return congestionService.getNearbyCongestion(LATITUDE, LONGITUDE);
    }

    private void givenRow(SeoulCityDataResponse.Row row) {
        when(seoulPublicDataService.fetchPopulation(anyString())).thenReturn(row);
    }

    private SeoulCityDataResponse.Row row(String congestLvl, List<SeoulCityDataResponse.Forecast> forecast) {
        return new SeoulCityDataResponse.Row(
                AREA_NAME, "POI068", congestLvl, "사람이 많아 붐벼요.",
                16000, 18000, SEOUL_TIME_KST, "Y", forecast);
    }

    /** KST 12:00부터 1시간 간격 — 서울시 응답과 같은 모양 */
    private List<SeoulCityDataResponse.Forecast> forecasts(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new SeoulCityDataResponse.Forecast(
                        FORECAST_START_KST.plusHours(i),
                        "약간 붐빔", 28000 + i * 1000, 30000 + i * 1000))
                .toList();
    }
}
