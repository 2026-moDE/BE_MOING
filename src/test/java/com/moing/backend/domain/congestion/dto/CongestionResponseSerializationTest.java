package com.moing.backend.domain.congestion.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.moing.backend.domain.review.dto.ReviewCreateResponse;
import com.moing.backend.domain.review.entity.CongestionLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지역 혼잡도 응답의 시각 포맷 테스트
 *
 * <p>혼잡도만 시각 포맷이 달라서 프론트가 두 기준을 다루게 됐던 적이 있다.
 * 리뷰 응답과 같은 문자열로 직렬화되는지 함께 비교한다.
 */
class CongestionResponseSerializationTest {

    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 10, 10, 2, 15);

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
    }

    @Test
    @DisplayName("기준 시각과 예측 시각이 ...Z 형식으로 직렬화된다")
    void 시각을_UTC_포맷으로_직렬화한다() throws Exception {
        String json = objectMapper.writeValueAsString(response());

        assertThat(json).contains("\"updated_at\":\"2026-10-10T02:15:00Z\"");
        assertThat(json).contains("\"time\":\"2026-10-10T03:00:00Z\"");
    }

    @Test
    @DisplayName("리뷰 응답의 created_at과 같은 문자열로 직렬화된다")
    void 리뷰_응답과_포맷이_같다() throws Exception {
        String congestionJson = objectMapper.writeValueAsString(response());
        String reviewJson = objectMapper.writeValueAsString(
                new ReviewCreateResponse(new ReviewCreateResponse.ReviewItem(1L, UPDATED_AT)));

        // 같은 LocalDateTime이 두 응답에서 같은 문자열이어야 한다
        assertThat(timeValue(congestionJson, "updated_at"))
                .isEqualTo(timeValue(reviewJson, "created_at"))
                .isEqualTo("2026-10-10T02:15:00Z");
    }

    @Test
    @DisplayName("응답 필드명과 구조는 그대로 유지한다")
    void 필드_구조가_유지된다() throws Exception {
        String json = objectMapper.writeValueAsString(response());

        assertThat(objectMapper.readTree(json).fieldNames()).toIterable()
                .containsExactly("area_name", "congestion_level", "congestion_message",
                        "population_min", "population_max", "updated_at", "forecast");
        assertThat(objectMapper.readTree(json).get("forecast").get(0).fieldNames()).toIterable()
                .containsExactly("time", "congestion_level", "population_min", "population_max");
    }

    private CongestionResponse response() {
        return new CongestionResponse(
                "성수카페거리", CongestionLevel.RELAXED, "여유로워요.", 24000, 26000, UPDATED_AT,
                List.of(new CongestionResponse.ForecastItem(
                        UPDATED_AT.plusMinutes(45), CongestionLevel.CROWDED, 28000, 30000)));
    }

    private String timeValue(String json, String field) throws Exception {
        return objectMapper.readTree(json).findValue(field).asText();
    }
}
