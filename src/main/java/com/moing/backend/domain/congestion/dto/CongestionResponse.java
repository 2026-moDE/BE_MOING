package com.moing.backend.domain.congestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.review.entity.CongestionLevel;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 지역 혼잡도 응답 (서울시 실시간 도시데이터 기반).
 *
 * <p>유저 제보 기반 혼잡도는 장소 상세(GET /api/places/{id})에서 따로 내려준다.
 */
public record CongestionResponse(
        @JsonProperty("area_name") String areaName,
        @JsonProperty("congestion_level") CongestionLevel congestionLevel,
        @JsonProperty("congestion_message") String congestionMessage,
        @JsonProperty("population_min") Integer populationMin,
        @JsonProperty("population_max") Integer populationMax,

        /** 서울시 데이터 기준 시각(KST). 화면에 "10.10 11:15 기준"으로 함께 띄운다. */
        @JsonProperty("updated_at") LocalDateTime updatedAt,

        List<ForecastItem> forecast
) {
    public record ForecastItem(
            LocalDateTime time,
            @JsonProperty("congestion_level") CongestionLevel congestionLevel,
            @JsonProperty("population_min") Integer populationMin,
            @JsonProperty("population_max") Integer populationMax
    ) {}
}
