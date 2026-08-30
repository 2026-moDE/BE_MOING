package com.moing.backend.domain.place.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 카카오 좌표→행정구역정보 응답 (coord2regioncode)
 * 같은 좌표에 대해 법정동("B")과 행정동("H") 두 건이 내려온다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoRegionResponse(
        List<Document> documents
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
            // "H" 행정동 / "B" 법정동
            @JsonProperty("region_type") String regionType,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("region_1depth_name") String region1depthName,
            @JsonProperty("region_2depth_name") String region2depthName,
            @JsonProperty("region_3depth_name") String region3depthName
    ) {}
}
