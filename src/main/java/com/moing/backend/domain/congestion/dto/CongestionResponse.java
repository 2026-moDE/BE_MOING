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

        List<ForecastItem> forecast,

        /**
         * 같은 지역의 실시간 날씨.
         *
         * <p>날씨 구역이 비어 있는 지역이 있고(가산디지털단지역·서울대공원) 서울시 응답이
         * 실패할 수도 있어 null로 내려갈 수 있다. 혼잡도의 부가 정보라 날씨가 없어도
         * 응답 자체는 성공으로 둔다.
         */
        Weather weather
) {
    public record ForecastItem(
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            LocalDateTime time,
            @JsonProperty("congestion_level") CongestionLevel congestionLevel,
            @JsonProperty("population_min") Integer populationMin,
            @JsonProperty("population_max") Integer populationMax
    ) {}

    /**
     * 실시간 날씨 (서울시 실시간 도시데이터 WEATHER_STTS).
     *
     * <p>서울시가 값이 없을 때 쓰는 {@code "-"}는 전부 null로 바꿔 담는다.
     * 숫자 필드가 래퍼 타입인 이유도 같다 — 0과 "값 없음"을 구분해야 한다.
     */
    public record Weather(
            /** 현재 기온(℃) */
            Double temperature,
            @JsonProperty("max_temperature") Double maxTemperature,
            @JsonProperty("min_temperature") Double minTemperature,

            /** 습도(%) */
            Integer humidity,

            /** 강수량(mm). 비·눈이 없으면 null */
            Double precipitation,

            /** 강수 형태 ("없음" / "비" / "비/눈" / "눈" / "소나기") */
            @JsonProperty("precipitation_type") String precipitationType,

            /** 강수 안내 문구 (예: "비 또는 눈 소식이 없어요.") */
            @JsonProperty("precipitation_message") String precipitationMessage,

            /**
             * 하늘 상태 ("맑음" / "구름많음" / "흐림").
             *
             * <p>서울시는 현재 하늘 상태를 주지 않아 <b>다음 정시 예보값</b>을 담는다.
             * 날씨 아이콘을 띄우려면 이 필드를 쓴다.
             */
            @JsonProperty("sky_status") String skyStatus,

            /** 강수 확률(%). 하늘 상태와 같이 다음 정시 예보값이다 */
            @JsonProperty("rain_chance") Integer rainChance,

            /** 미세먼지(㎍/㎥)와 등급 ("좋음" / "보통" / "나쁨" / "매우나쁨") */
            Integer pm10,
            @JsonProperty("pm10_index") String pm10Index,

            /** 초미세먼지(㎍/㎥)와 등급 */
            Integer pm25,
            @JsonProperty("pm25_index") String pm25Index,

            /** 통합대기환경 등급과 안내 문구 */
            @JsonProperty("air_index") String airIndex,
            @JsonProperty("air_message") String airMessage,

            /** 자외선 지수와 등급·안내 문구 */
            @JsonProperty("uv_index") Integer uvIndex,
            @JsonProperty("uv_index_level") String uvIndexLevel,
            @JsonProperty("uv_message") String uvMessage,

            /**
             * 일출·일몰 시각 ("HH:mm").
             *
             * <p>다른 시각 필드와 달리 <b>KST</b>다. 서울시가 날짜 없이 시·분만 주므로
             * UTC로 옮기면 날짜가 밀려 오히려 틀린 값처럼 보인다.
             */
            String sunrise,
            String sunset,

            /** 날씨 데이터 기준 시각. 혼잡도의 updated_at과 기준 시각이 다를 수 있다 */
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            @JsonProperty("updated_at") LocalDateTime updatedAt
    ) {}
}
