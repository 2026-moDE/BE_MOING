package com.moing.backend.domain.congestion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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

        /**
         * 서울시 데이터 기준 시각. 호출 시각이 아니라 서울시가 집계를 끝낸 시각이라
         * 화면에 함께 띄운다. 서울시는 KST로 내려주지만 SeoulCityDataResponse가
         * UTC로 바꿔 담으므로 다른 응답의 시각과 기준이 같다.
         */
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        @JsonProperty("updated_at") LocalDateTime updatedAt,

        List<ForecastItem> forecast
) {
    public record ForecastItem(
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            LocalDateTime time,
            @JsonProperty("congestion_level") CongestionLevel congestionLevel,
            @JsonProperty("population_min") Integer populationMin,
            @JsonProperty("population_max") Integer populationMax
    ) {}
}
