package com.moing.backend.global.infra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 서울시 실시간 도시데이터 지역 목록 응답.
 *
 * <p>좌표 키가 뒤집혀 있다. {@code x}가 위도, {@code y}가 경도다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeoulHotspotResponse(
        Integer total,
        @JsonProperty("row") List<Row> rows
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(
            @JsonProperty("area_nm") String areaNm,
            String category,
            @JsonProperty("x") Double latitude,
            @JsonProperty("y") Double longitude
    ) {
        /** 좌표나 지역명이 비어 오면 최근접 매칭에 쓸 수 없다 */
        public boolean isUsable() {
            return areaNm != null && !areaNm.isBlank() && latitude != null && longitude != null;
        }
    }
}
