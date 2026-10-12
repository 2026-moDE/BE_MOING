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
 * <p>고른 지역의 혼잡도는 {@code GET /api/congestion/areas/{id}}로 <b>id를 넘겨</b> 조회한다.
 * 좌표를 넘겨 최근접 지역을 다시 찾게 하면 안 된다 — 121곳 중 314쌍이 서로 2km 안에 있고
 * 가장 가까운 쌍은 74m밖에 떨어져 있지 않아(동대문 관광특구 ↔ DDP) 옆 지역이 잡힌다.
 *
 * <p>좌표는 지도에 핀을 찍는 용도로만 함께 내려준다.
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
