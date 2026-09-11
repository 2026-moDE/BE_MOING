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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 서울시 실시간 인구 API 클라이언트 테스트
 * 응답 본문은 실제 API(sample 키)를 호출해 받은 그대로를 픽스처로 두고 검증한다.
 */
class SeoulPublicDataServiceTest {

    private static final String API_KEY = "test-key";
    private static final String AREA_NAME = "광화문·덕수궁";

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
    @DisplayName("실제 응답 형식을 그대로 파싱해 혼잡도·인구수를 꺼낸다")
    void 실제_응답을_파싱한다() throws IOException {
        mockServer.expect(requestTo(expectedUri(AREA_NAME)))
                .andRespond(withSuccess(realResponse(), MediaType.APPLICATION_JSON));

        SeoulCityDataResponse.Row row = seoulPublicDataService.fetchPopulation(AREA_NAME);

        assertThat(row).isNotNull();
        assertThat(row.areaNm()).isEqualTo(AREA_NAME);
        assertThat(row.areaCongestLvl()).isEqualTo("여유");
        assertThat(row.areaPpltnMin()).isEqualTo(18000);
        assertThat(row.areaPpltnMax()).isEqualTo(20000);
    }

    @Test
    @DisplayName("지역명의 한글·가운뎃점을 인코딩해 호출한다")
    void 지역명을_인코딩해_호출한다() throws IOException {
        mockServer.expect(requestTo(expectedUri(AREA_NAME)))
                .andRespond(withSuccess(realResponse(), MediaType.APPLICATION_JSON));

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

    private String realResponse() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixture/seoul-citydata-ppltn.json")) {
            assertThat(in).as("픽스처 파일").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
