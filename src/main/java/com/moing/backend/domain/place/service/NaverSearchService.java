package com.moing.backend.domain.place.service;

import com.moing.backend.domain.place.dto.NaverLocalResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 네이버 장소 검색 API 클라이언트
 * GET https://openapi.naver.com/v1/search/local.json
 */
@Service
@RequiredArgsConstructor
public class NaverSearchService {

    private static final String NCLOUD_PLACE_URL = "https://openapi.naver.com/v1/search/local.json";
    private static final int MAX_DISPLAY = 30;

    @Value("${naver.api-key-id}")
    private String apiKeyId;

    @Value("${naver.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    /**
     * @param query   검색어 (예: "강남 카페", "홍대 맛집")
     * @return 네이버 검색 결과
     */
    public NaverLocalResponse search(String query) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Naver-Client-Id", apiKeyId);
        headers.set("X-Naver-Client-Secret", apiKey);

        URI uri = UriComponentsBuilder.fromUriString(NCLOUD_PLACE_URL)
                .queryParam("query", query)
                .queryParam("display", MAX_DISPLAY)
                .encode()
                .build()
                .toUri();

        return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), NaverLocalResponse.class)
                .getBody();
    }
}
