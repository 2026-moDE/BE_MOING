package com.moing.backend.global.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 서울시 실시간 인구 API 클라이언트 테스트
 * 응답 본문은 실제 API를 호출해 받은 그대로를 픽스처로 두고 검증한다.
 */
class SeoulPublicDataServiceTest {

    private static final String API_KEY = "test-key";
    private static final String AREA_NAME = "광화문·덕수궁";
    private static final String FORECAST_AREA_NAME = "성수카페거리";

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private SeoulPublicDataService seoulPublicDataService;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        seoulPublicDataService = new SeoulPublicDataService(restTemplate);
        ReflectionTestUtils.setField(seoulPublicDataService, "apiKey", API_KEY);
    }

    @Test
    @DisplayName("실제 응답 형식을 그대로 파싱해 혼잡도·설명·인구수·기준 시각을 꺼낸다")
    void 실제_응답을_파싱한다() throws IOException {
        mockServer.expect(requestTo(expectedUri(AREA_NAME)))
                .andRespond(withSuccess(fixture("seoul-citydata-ppltn.json"), MediaType.APPLICATION_JSON));

        SeoulCityDataResponse.Row row = seoulPublicDataService.fetchPopulation(AREA_NAME);

        assertThat(row).isNotNull();
        assertThat(row.areaNm()).isEqualTo(AREA_NAME);
        assertThat(row.areaCd()).isEqualTo("POI009");
        assertThat(row.areaCongestLvl()).isEqualTo("여유");
        assertThat(row.areaCongestMsg()).startsWith("사람이 몰려있을 가능성이 낮고");
        assertThat(row.areaPpltnMin()).isEqualTo(18000);
        assertThat(row.areaPpltnMax()).isEqualTo(20000);
        // 기준 시각은 "yyyy-MM-dd HH:mm" KST로 내려온다 (ISO 형식이 아니다)
        assertThat(row.ppltnTime()).isEqualTo(LocalDateTime.of(2026, 9, 7, 20, 35));
    }

    @Test
    @DisplayName("예측은 1시간 간격 12건이 그대로 파싱된다")
    void 예측_12건을_파싱한다() throws IOException {
        mockServer.expect(requestTo(expectedUri(FORECAST_AREA_NAME)))
                .andRespond(withSuccess(fixture("seoul-citydata-ppltn-forecast.json"), MediaType.APPLICATION_JSON));

        SeoulCityDataResponse.Row row = seoulPublicDataService.fetchPopulation(FORECAST_AREA_NAME);

        assertThat(row).isNotNull();
        assertThat(row.fcstYn()).isEqualTo("Y");
        assertThat(row.fcstPpltn()).hasSize(12);

        SeoulCityDataResponse.Forecast first = row.fcstPpltn().get(0);
        // 기준 시각(11:15)이 아니라 다음 정시부터 시작한다
        assertThat(first.fcstTime()).isEqualTo(LocalDateTime.of(2026, 10, 10, 12, 0));
        assertThat(first.fcstCongestLvl()).isEqualTo("약간 붐빔");
        assertThat(first.fcstPpltnMin()).isEqualTo(28000);
        assertThat(first.fcstPpltnMax()).isEqualTo(30000);

        // 1시간 간격이라 마지막 항목이 기준 시각으로부터 12시간 뒤를 덮는다
        assertThat(row.fcstPpltn().get(11).fcstTime())
                .isEqualTo(LocalDateTime.of(2026, 10, 10, 23, 0));
    }

    @Test
    @DisplayName("지역명의 한글·가운뎃점을 인코딩해 호출한다")
    void 지역명을_인코딩해_호출한다() throws IOException {
        mockServer.expect(requestTo(expectedUri(AREA_NAME)))
                .andRespond(withSuccess(fixture("seoul-citydata-ppltn.json"), MediaType.APPLICATION_JSON));

        seoulPublicDataService.fetchPopulation(AREA_NAME);

        mockServer.verify();
    }

    @Test
    @DisplayName("데이터가 비어 있으면 null을 반환한다")
    void 빈_응답이면_null이다() {
        mockServer.expect(requestTo(expectedUri(AREA_NAME)))
                .andRespond(withSuccess("{\"SeoulRtd.citydata_ppltn\":[]}", MediaType.APPLICATION_JSON));

        assertThat(seoulPublicDataService.fetchPopulation(AREA_NAME)).isNull();
    }

    @Test
    @DisplayName("서울시가 HTTP 200에 에러 코드만 담아 보내도 null을 반환한다")
    void 에러_코드_응답이면_null이다() {
        // 없는 지역명을 넣으면 상태 코드는 200이고 본문만 에러다
        mockServer.expect(requestTo(expectedUri(AREA_NAME)))
                .andRespond(withSuccess(
                        "{\"RESULT.CODE\":\"ERROR-500\",\"RESULT.MESSAGE\":\"서버 오류입니다.\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(seoulPublicDataService.fetchPopulation(AREA_NAME)).isNull();
    }

    @Test
    @DisplayName("API 호출이 실패해도 예외를 던지지 않고 null을 반환한다")
    void 호출_실패시_null이다() {
        mockServer.expect(requestTo(expectedUri(AREA_NAME)))
                .andRespond(withServerError());

        assertThat(seoulPublicDataService.fetchPopulation(AREA_NAME)).isNull();
    }

    private String expectedUri(String areaName) {
        return "http://openapi.seoul.go.kr:8088/" + API_KEY + "/json/citydata_ppltn/1/5/"
                + URLEncoder.encode(areaName, StandardCharsets.UTF_8);
    }

    private String fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixture/" + name)) {
            assertThat(in).as("픽스처 파일 %s", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
