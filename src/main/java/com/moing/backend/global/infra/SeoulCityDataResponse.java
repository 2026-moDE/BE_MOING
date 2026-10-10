package com.moing.backend.global.infra;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 서울시 실시간 인구 API 응답.
 * "SeoulRtd.citydata_ppltn"은 래핑 객체 없이 지역 데이터 배열이 바로 들어온다.
 *
 * <p>조회에 실패하면 HTTP 200에 {@code {"RESULT.CODE":"ERROR-500"}}만 담겨 오므로
 * 이 키가 비면 데이터가 없는 것으로 본다.
 *
 * <p>인구수는 모두 문자열("24000")로 내려온다. Jackson이 숫자로 변환해 준다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeoulCityDataResponse(
        @JsonProperty("SeoulRtd.citydata_ppltn") List<Row> rows,
        @JsonProperty("RESULT.CODE") String resultCode
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(
            @JsonProperty("AREA_NM") String areaNm,
            @JsonProperty("AREA_CD") String areaCd,
            @JsonProperty("AREA_CONGEST_LVL") String areaCongestLvl,
            @JsonProperty("AREA_CONGEST_MSG") String areaCongestMsg,
            @JsonProperty("AREA_PPLTN_MIN") Integer areaPpltnMin,
            @JsonProperty("AREA_PPLTN_MAX") Integer areaPpltnMax,

            /** 데이터 기준 시각(KST). 호출 시각이 아니라 서울시가 집계를 끝낸 시각이다. */
            @JsonProperty("PPLTN_TIME")
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime ppltnTime,

            @JsonProperty("FCST_YN") String fcstYn,

            /** 1시간 간격 12건. 기준 시각이 아니라 다음 정시부터 시작한다. */
            @JsonProperty("FCST_PPLTN") List<Forecast> fcstPpltn
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Forecast(
            @JsonProperty("FCST_TIME")
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime fcstTime,

            @JsonProperty("FCST_CONGEST_LVL") String fcstCongestLvl,
            @JsonProperty("FCST_PPLTN_MIN") Integer fcstPpltnMin,
            @JsonProperty("FCST_PPLTN_MAX") Integer fcstPpltnMax
    ) {}
}
