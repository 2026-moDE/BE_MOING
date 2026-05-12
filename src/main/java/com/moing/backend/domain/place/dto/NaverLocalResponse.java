package com.moing.backend.domain.place.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 네이버 지역 검색 API 응답 DTO
 * https://openapi.naver.com/v1/search/local.json
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverLocalResponse(
        int total,
        int display,
        List<Item> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String title,
            String category,
            String address,
            String roadAddress,
            String mapx,        // 경도(Longitude) * 10^7 좌표
            String mapy         // 위도(Latitude) * 10^7 좌표
    ) {
        /** HTML 태그를 제거한 순수 장소명 반환 */
        public String cleanTitle() {
            return title == null ? "" : title.replaceAll("<[^>]+>", "");
        }
    }
}
