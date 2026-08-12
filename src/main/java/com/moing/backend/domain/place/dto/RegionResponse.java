package com.moing.backend.domain.place.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 좌표의 행정동 정보 (현재 위치 표시용)
 * 바다·해외 등 행정구역을 찾을 수 없는 좌표면 모든 필드가 null이다.
 */
public record RegionResponse(
        @JsonProperty("region_1depth_name") String region1depthName,
        @JsonProperty("region_2depth_name") String region2depthName,
        // 화면에 노출할 "~~동"
        @JsonProperty("region_3depth_name") String region3depthName
) {
    public static RegionResponse empty() {
        return new RegionResponse(null, null, null);
    }
}
