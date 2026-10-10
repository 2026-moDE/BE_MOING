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
 * 서울시 실시간 인구 공공 API 클라이언트
 * GET http://openapi.seoul.go.kr:8088/{key}/json/citydata_ppltn/1/5/{지역명}
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
}
