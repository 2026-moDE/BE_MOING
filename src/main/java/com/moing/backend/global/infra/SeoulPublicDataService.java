package com.moing.backend.global.infra;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
     * 특정 지역의 실시간 인구 데이터 조회
     *
     * @param areaName 서울시 지역명 (예: "성수동", "홍대입구역")
     * @return API 응답 row (없으면 null)
     */
    public SeoulCityDataResponse.Row fetchPopulation(String areaName) {
        URI uri = UriComponentsBuilder.fromUriString(BASE_URL)
                .pathSegment(apiKey, "json", "citydata_ppltn", "1", "5", areaName)
                .encode()
                .build()
                .toUri();

        try {
            SeoulCityDataResponse response = restTemplate.getForObject(uri, SeoulCityDataResponse.class);
            if (response == null || response.rows() == null || response.rows().isEmpty()) {
                return null;
            }
            return response.rows().get(0);
        } catch (RestClientException e) {
            log.warn("서울시 실시간 인구 API 호출 실패 [{}]: {}", areaName, e.getMessage());
            return null;
        }
    }
}
