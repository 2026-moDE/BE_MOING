package com.moing.backend.domain.place.service;

import com.moing.backend.domain.place.dto.KakaoLocalResponse;
import com.moing.backend.domain.place.dto.KakaoRegionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoSearchService {

    private static final String KAKAO_LOCAL_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
    private static final String KAKAO_REGION_URL = "https://dapi.kakao.com/v2/local/geo/coord2regioncode.json";
    private static final int MAX_SIZE = 15;

    @Value("${kakao.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    /**
     * 키워드로 장소 검색 (좌표 기반 거리순 정렬)
     *
     * @param keyword   검색 키워드
     * @param longitude 경도 (nullable)
     * @param latitude  위도 (nullable)
     * @param radius    반경 미터 (nullable)
     */
    public KakaoLocalResponse search(String keyword, Double longitude, Double latitude, Integer radius) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + apiKey);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(KAKAO_LOCAL_URL)
                .queryParam("query", keyword)
                .queryParam("size", MAX_SIZE);

        if (longitude != null && latitude != null) {
            builder.queryParam("x", longitude)
                    .queryParam("y", latitude)
                    .queryParam("sort", "distance");
            if (radius != null) {
                builder.queryParam("radius", radius);
            }
        }

        URI uri = builder.encode().build().toUri();

        log.info("카카오 검색 API 요청: uri={}", uri);

        try {
            KakaoLocalResponse response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), KakaoLocalResponse.class)
                    .getBody();
            log.info("카카오 검색 API 응답: documents={}", response != null && response.documents() != null ? response.documents().size() : "null");
            return response;
        } catch (RestClientException e) {
            log.warn("카카오 검색 API 호출 실패: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 좌표를 행정구역 정보로 변환 (현재 위치의 "~~동" 표시용)
     *
     * @param latitude  위도 (WGS84)
     * @param longitude 경도 (WGS84)
     * @return 실패 시 null
     */
    public KakaoRegionResponse coord2Region(double latitude, double longitude) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + apiKey);

        URI uri = UriComponentsBuilder.fromUriString(KAKAO_REGION_URL)
                .queryParam("x", longitude)
                .queryParam("y", latitude)
                .encode().build().toUri();

        try {
            return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), KakaoRegionResponse.class)
                    .getBody();
        } catch (RestClientException e) {
            log.warn("카카오 좌표→행정구역 API 호출 실패: {}", e.getMessage(), e);
            return null;
        }
    }
}
