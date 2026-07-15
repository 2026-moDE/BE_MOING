package com.moing.backend.global.infra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SeoulCityDataResponse(
        @JsonProperty("SeoulRtd.citydata_ppltn") CityDataPpltn cityDataPpltn
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CityDataPpltn(List<Row> row) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(
            @JsonProperty("AREA_NM") String areaNm,
            @JsonProperty("AREA_CONGEST_LVL") String areaCongestLvl,
            @JsonProperty("AREA_PPLTN_MIN") Integer areaPpltnMin,
            @JsonProperty("AREA_PPLTN_MAX") Integer areaPpltnMax
    ) {}
}
