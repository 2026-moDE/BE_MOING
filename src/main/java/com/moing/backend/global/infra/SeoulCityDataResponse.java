package com.moing.backend.global.infra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 서울시 실시간 인구 API 응답.
 * "SeoulRtd.citydata_ppltn"은 래핑 객체 없이 지역 데이터 배열이 바로 들어온다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeoulCityDataResponse(
        @JsonProperty("SeoulRtd.citydata_ppltn") List<Row> rows
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(
            @JsonProperty("AREA_NM") String areaNm,
            @JsonProperty("AREA_CONGEST_LVL") String areaCongestLvl,
            @JsonProperty("AREA_PPLTN_MIN") Integer areaPpltnMin,
            @JsonProperty("AREA_PPLTN_MAX") Integer areaPpltnMax
    ) {}
}
