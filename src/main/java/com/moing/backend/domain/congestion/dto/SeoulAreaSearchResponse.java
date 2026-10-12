package com.moing.backend.domain.congestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.congestion.entity.SeoulArea;

import java.util.List;

/**
 * 서울시 도시데이터 지역 목록·검색 응답.
 *
 * <p>사용자가 혼잡도를 볼 지역을 직접 고르는 화면에서 쓴다. 고를 수 있는 지역은
 * 서울시가 실시간 인구를 내려주는 121곳으로 한정되므로 {@code seoul_areas}만 조회한다.
 *
 * <p>좌표를 함께 내려주는 이유는 선택한 지역의 혼잡도를 바로
 * {@code GET /api/congestion/nearby}로 조회할 수 있게 하기 위함이다.
 * (지역 중심 좌표를 그대로 넘기면 자기 자신이 최근접 지역으로 매칭된다.)
 */
public record SeoulAreaSearchResponse(List<AreaItem> areas) {

    public record AreaItem(
            Long id,
            @JsonProperty("area_name") String areaName,
            String category,
            double latitude,
            double longitude
    ) {
        public static AreaItem from(SeoulArea area) {
            return new AreaItem(
                    area.getId(),
                    area.getAreaNm(),
                    area.getCategory(),
                    area.getLatitude().doubleValue(),
                    area.getLongitude().doubleValue());
        }
    }

    public static SeoulAreaSearchResponse from(List<SeoulArea> areas) {
        return new SeoulAreaSearchResponse(areas.stream().map(AreaItem::from).toList());
    }
}
