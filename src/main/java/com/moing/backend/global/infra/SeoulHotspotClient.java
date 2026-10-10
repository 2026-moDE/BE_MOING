package com.moing.backend.global.infra;

import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 서울시 실시간 도시데이터 지역 목록 클라이언트
 * GET https://data.seoul.go.kr/SeoulRtd/api/hotspot-category?page=1&count=500&category={분류}
 *
 * <p>지역명과 공식 좌표를 함께 내려주는 유일한 경로다. 실시간 인구 API(citydata_ppltn)는
 * 지역명이나 POI 코드를 받아야만 답하고 목록을 주지 않으며, 좌표도 포함하지 않는다.
 *
 * <p>인증키가 필요 없어 열린데이터광장 호출 한도를 쓰지 않는 대신,
 * 공식 Open API가 아니라 서울시 사이트가 쓰는 조회 경로다. 응답 형식이 바뀔 수 있으니
 * 런타임 조회에는 쓰지 않고 지역 목록 동기화에서만 호출한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeoulHotspotClient {

    private static final String URL = "https://data.seoul.go.kr/SeoulRtd/api/hotspot-category";

    // Referer가 없으면 302로 에러 페이지에 던져진다. User-Agent는 없어도 된다.
    private static final String REFERER = "https://data.seoul.go.kr/SeoulRtd/citydata";

    // 서울시 지역 분류. 목록 전체를 받으려면 분류별로 나눠 호출해야 한다.
    static final List<String> CATEGORIES =
            List.of("관광특구", "고궁·문화유산", "공원", "발달상권", "인구밀집지역");

    private static final int PAGE_SIZE = 500;   // 분류별 최대 48곳이라 한 페이지로 충분하다

    private final RestTemplate restTemplate;

    /**
     * 전체 지역 목록 조회.
     *
     * <p>분류 하나라도 실패하면 예외를 던진다. 일부만 성공한 결과로 동기화하면
     * 받아오지 못한 분류의 지역이 테이블에서 지워져, 그 지역을 매칭하던 요청이 404가 된다.
     *
     * @throws CustomException 분류 중 하나라도 조회에 실패한 경우
     */
    public List<SeoulHotspotResponse.Row> fetchAllAreas() {
        List<SeoulHotspotResponse.Row> areas = new ArrayList<>();

        for (String category : CATEGORIES) {
            List<SeoulHotspotResponse.Row> rows = fetchCategory(category);
            if (rows == null) {
                throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
            }
            areas.addAll(rows);
        }

        return areas;
    }

    private List<SeoulHotspotResponse.Row> fetchCategory(String category) {
        URI uri = UriComponentsBuilder.fromUriString(URL)
                .queryParam("page", 1)
                .queryParam("count", PAGE_SIZE)
                .queryParam("category", category)
                .encode()
                .build()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.REFERER, REFERER);

        try {
            SeoulHotspotResponse response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(headers), SeoulHotspotResponse.class).getBody();

            if (response == null || response.rows() == null || response.rows().isEmpty()) {
                log.warn("서울시 지역 목록 응답이 비어 있음 [{}]", category);
                return null;
            }
            return response.rows();
        } catch (RestClientException e) {
            log.warn("서울시 지역 목록 조회 실패 [{}]: {}", category, e.getMessage());
            return null;
        }
    }
}
