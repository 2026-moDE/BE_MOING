package com.moing.backend.global.infra;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 서울시 실시간 도시데이터(citydata) 응답 중 날씨 구역.
 *
 * <p>날씨만 주는 하위 서비스가 없어 전체 도시데이터를 받아 {@code WEATHER_STTS}만 꺼낸다.
 * 응답에는 도로소통·주차장·따릉이 등 쓰지 않는 구역도 함께 담겨 오므로(지역당 100KB 내외)
 * 5분 캐싱을 전제로 호출한다.
 *
 * <p>구역이 {@code CITYDATA.WEATHER_STTS.WEATHER_STTS}로 한 번 더 감싸여 있다.
 * 예보도 {@code FCST24HOURS.FCST24HOURS}로 같은 모양이다.
 *
 * <p><b>모든 값이 문자열로 내려온다.</b> 강수량처럼 값이 없을 때 {@code "-"}를 쓰는 필드가
 * 있어 숫자로 바로 받으면 역직렬화가 응답 전체를 들고 실패한다. 그래서 문자열로 담고
 * 숫자 변환은 쓰는 쪽에서 한다.
 *
 * <p>시각은 KST로 들어오므로 {@link SeoulCityDataResponse}와 같이 UTC로 바꿔 담는다.
 * 다만 일출·일몰은 날짜 없는 "HH:mm"이라 UTC로 옮기면 날짜가 밀려 더 헷갈리므로
 * 받은 그대로(KST 벽시계) 둔다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeoulWeatherResponse(
        @JsonProperty("CITYDATA") CityData cityData,
        @JsonProperty("RESULT") Result result
) {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 서울시가 내려준 KST 시각을 UTC로 옮긴다 */
    private static LocalDateTime toUtc(LocalDateTime seoulTime) {
        if (seoulTime == null) return null;
        return seoulTime.atZone(KST)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    /**
     * 날씨 구역만 꺼낸다.
     *
     * <p>조회가 실패하면 CITYDATA 자체가 없고, 성공해도 날씨 구역이 {@code null}인 지역이
     * 있다(가산디지털단지역·서울대공원). 중간 어디가 비어도 null로 끝낸다.
     */
    public Weather weather() {
        if (cityData == null || cityData.weatherStts() == null) return null;
        return cityData.weatherStts().weather();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(@JsonProperty("RESULT.CODE") String code) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CityData(@JsonProperty("WEATHER_STTS") WeatherWrapper weatherStts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WeatherWrapper(@JsonProperty("WEATHER_STTS") Weather weather) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Weather(
            /** 데이터 기준 시각 (UTC로 바꿔 담는다) */
            @JsonProperty("WEATHER_TIME")
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime weatherTime,

            @JsonProperty("TEMP") String temp,
            @JsonProperty("MAX_TEMP") String maxTemp,
            @JsonProperty("MIN_TEMP") String minTemp,
            @JsonProperty("HUMIDITY") String humidity,

            /** 강수량(mm). 비·눈이 없으면 "-"로 온다 */
            @JsonProperty("PRECIPITATION") String precipitation,
            @JsonProperty("PRECPT_TYPE") String precptType,
            @JsonProperty("PCP_MSG") String pcpMsg,

            /** "HH:mm" KST. 날짜가 없어 UTC로 옮기지 않는다 */
            @JsonProperty("SUNRISE") String sunrise,
            @JsonProperty("SUNSET") String sunset,

            @JsonProperty("UV_INDEX_LVL") String uvIndexLvl,
            @JsonProperty("UV_INDEX") String uvIndex,
            @JsonProperty("UV_MSG") String uvMsg,

            @JsonProperty("PM25_INDEX") String pm25Index,
            @JsonProperty("PM25") String pm25,
            @JsonProperty("PM10_INDEX") String pm10Index,
            @JsonProperty("PM10") String pm10,
            @JsonProperty("AIR_IDX") String airIdx,
            @JsonProperty("AIR_MSG") String airMsg,

            /** 1시간 간격 24건. 기준 시각이 아니라 다음 정시부터 시작한다 */
            @JsonProperty("FCST24HOURS") ForecastWrapper fcst24Hours
    ) {
        public Weather {
            weatherTime = toUtc(weatherTime);
        }

        /** 다음 정시 예보. 현재 하늘 상태(맑음/흐림)는 응답에 없어 이걸로 대신한다 */
        public HourlyForecast nextHour() {
            if (fcst24Hours == null || fcst24Hours.forecasts() == null
                    || fcst24Hours.forecasts().isEmpty()) {
                return null;
            }
            return fcst24Hours.forecasts().get(0);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ForecastWrapper(@JsonProperty("FCST24HOURS") List<HourlyForecast> forecasts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HourlyForecast(
            /** 예보 시각 (UTC로 바꿔 담는다). 인구 예측과 달리 "yyyyMMddHHmm"로 온다 */
            @JsonProperty("FCST_DT")
            @JsonFormat(pattern = "yyyyMMddHHmm") LocalDateTime fcstDt,

            @JsonProperty("TEMP") String temp,
            @JsonProperty("PRECPT_TYPE") String precptType,
            @JsonProperty("RAIN_CHANCE") String rainChance,
            @JsonProperty("SKY_STTS") String skyStts
    ) {
        public HourlyForecast {
            fcstDt = toUtc(fcstDt);
        }
    }
}
