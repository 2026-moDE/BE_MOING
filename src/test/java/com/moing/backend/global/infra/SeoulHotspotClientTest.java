package com.moing.backend.global.infra;

import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 서울시 지역 목록 클라이언트 테스트
 * 응답 본문은 실제 호출로 받은 그대로를 픽스처로 둔다.
 */
class SeoulHotspotClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private SeoulHotspotClient seoulHotspotClient;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        seoulHotspotClient = new SeoulHotspotClient(restTemplate);
    }

    @Test
    @DisplayName("분류 5개를 모두 호출하고 Referer를 붙인다")
    void 분류별로_호출하며_Referer를_붙인다() throws IOException {
        // Referer가 없으면 서울시가 302로 에러 페이지에 던진다
        mockServer.expect(times(SeoulHotspotClient.CATEGORIES.size()), anything())
                .andExpect(header(HttpHeaders.REFERER, "https://data.seoul.go.kr/SeoulRtd/citydata"))
                .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

        seoulHotspotClient.fetchAllAreas();

        mockServer.verify();
    }

    @Test
    @DisplayName("x가 위도, y가 경도로 매핑된다")
    void 좌표_키가_뒤집혀_있다() throws IOException {
        mockServer.expect(times(SeoulHotspotClient.CATEGORIES.size()), anything())
                .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

        List<SeoulHotspotResponse.Row> areas = seoulHotspotClient.fetchAllAreas();

        SeoulHotspotResponse.Row gangnam = areas.stream()
                .filter(a -> a.areaNm().equals("강남 MICE 관광특구"))
                .findFirst()
                .orElseThrow();

        assertThat(gangnam.latitude()).isCloseTo(37.511, org.assertj.core.data.Offset.offset(0.001));
        assertThat(gangnam.longitude()).isCloseTo(127.060, org.assertj.core.data.Offset.offset(0.001));
        assertThat(gangnam.category()).isEqualTo("관광특구");
        assertThat(gangnam.isUsable()).isTrue();
    }

    @Test
    @DisplayName("분류 하나라도 실패하면 502로 중단한다")
    void 일부_실패시_예외를_던진다() {
        // 일부만 성공한 결과로 동기화하면 못 받은 분류의 지역이 테이블에서 지워진다
        mockServer.expect(anything()).andRespond(withServerError());

        assertThatThrownBy(() -> seoulHotspotClient.fetchAllAreas())
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }

    @Test
    @DisplayName("빈 목록이 오면 502로 중단한다")
    void 빈_목록이면_예외를_던진다() {
        mockServer.expect(anything())
                .andRespond(withSuccess("{\"total\":0,\"row\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> seoulHotspotClient.fetchAllAreas())
                .isInstanceOf(CustomException.class);
    }

    private String fixture() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixture/seoul-hotspot-category.json")) {
            assertThat(in).as("지역 목록 픽스처").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
