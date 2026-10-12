package com.moing.backend.global.infra;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 서울시 실시간 도시데이터 공공 API 클라이언트
 *
 * <ul>
 *   <li>인구: GET http://openapi.seoul.go.kr:8088/{key}/json/citydata_ppltn/1/5/{지역명}</li>
 *   <li>날씨: GET http://openapi.seoul.go.kr:8088/{key}/json/citydata/1/5/{지역명}</li>
 * </ul>
 *
 * <p>날씨만 주는 하위 서비스가 없어 날씨는 전체 도시데이터를 받아 쓴다. 그 응답에 인구도
 * 들어 있지만 한 번에 받지 않는 이유는, 인구 쪽은 응답이 작고 실패 시 502로 끊어야 하는
 * 반면 날씨는 없어도 응답을 내보내야 해서 실패를 다루는 방식이 다르기 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeoulPublicDataService {

    private static final String BASE_URL = "http://openapi.seoul.go.kr:8088";

    @Value("${seoul.city-data-api-key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    /**
     * 특정 지역의 실시간 인구 데이터 조회.
     *
     * <p>서울시가 5분 주기로 갱신하므로 같은 주기로 캐싱한다(CacheConfig). 캐싱이 없으면
     * 바텀시트를 열 때마다 외부 호출이 나가 호출량과 응답 지연이 함께 커진다.
     * 실패(null)는 캐싱하지 않아 다음 요청이 곧바로 재시도한다.
     *
     * @param areaName 서울시 지역명 (예: "성수카페거리", "홍대입구역(2호선)")
     * @return API 응답 row (없으면 null)
     */
    @Cacheable(cacheNames = "seoulCongestion", key = "#areaName", unless = "#result == null")
    public SeoulCityDataResponse.Row fetchPopulation(String areaName) {
        URI uri = UriComponentsBuilder.fromUriString(BASE_URL)
                .pathSegment(apiKey, "json", "citydata_ppltn", "1", "5", areaName)
                .encode()
                .build()
                .toUri();

        try {
            SeoulCityDataResponse response = restTemplate.getForObject(uri, SeoulCityDataResponse.class);
            if (response == null || response.rows() == null || response.rows().isEmpty()) {
                // 지역명이 틀렸거나 서울시 쪽 장애. 둘 다 HTTP 200 + RESULT.CODE로 내려온다.
                log.warn("서울시 실시간 인구 데이터 없음 [{}]: {}",
                        areaName, response == null ? "응답 없음" : response.resultCode());
                return null;
            }
            return response.rows().get(0);
        } catch (RestClientException e) {
            log.warn("서울시 실시간 인구 API 호출 실패 [{}]: {}", areaName, e.getMessage());
            return null;
        }
    }

    /**
     * 특정 지역의 실시간 날씨 데이터 조회.
     *
     * <p>인구와 달리 날씨만 주는 하위 서비스가 없어 전체 도시데이터({@code citydata})를 받는다.
     * 응답에 도로소통·주차장 등 쓰지 않는 구역까지 담겨 지역당 100KB 내외라, 인구와 같은
     * 5분 캐싱을 반드시 함께 둔다.
     *
     * <p>조회에 성공해도 날씨 구역이 비어 있는 지역이 있어(가산디지털단지역·서울대공원)
     * 그 경우도 null로 돌려준다. 날씨는 혼잡도의 부가 정보라 호출부가 없는 채로 응답한다.
     *
     * @param areaName 서울시 지역명 (예: "성수카페거리", "홍대입구역(2호선)")
     * @return 날씨 구역 (없으면 null)
     */
    @Cacheable(cacheNames = "seoulWeather", key = "#areaName", unless = "#result == null")
    public SeoulWeatherResponse.Weather fetchWeather(String areaName) {
        URI uri = UriComponentsBuilder.fromUriString(BASE_URL)
                .pathSegment(apiKey, "json", "citydata", "1", "5", areaName)
                .encode()
                .build()
                .toUri();

        try {
            SeoulWeatherResponse response = restTemplate.getForObject(uri, SeoulWeatherResponse.class);
            if (response == null || response.weather() == null) {
                log.warn("서울시 실시간 날씨 데이터 없음 [{}]: {}", areaName,
                        response == null || response.result() == null ? "응답 없음" : response.result().code());
                return null;
            }
            return response.weather();
        } catch (RestClientException e) {
            log.warn("서울시 실시간 날씨 API 호출 실패 [{}]: {}", areaName, e.getMessage());
            return null;
        }
    }
}
