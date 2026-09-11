package com.moing.backend.domain.congestion.service;

import com.moing.backend.domain.congestion.dto.CongestionResponse;
import com.moing.backend.domain.place.entity.Place;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.review.entity.CongestionLevel;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.global.infra.SeoulCityDataResponse;
import com.moing.backend.global.infra.SeoulPublicDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CongestionService {

    private static final int PLACE_SEARCH_RADIUS = 300;   // 지역 중심 반경 300m 내 장소 검색
    private static final int USER_REVIEW_THRESHOLD = 3;   // USER 데이터 사용 기준 리뷰 수

    private final PlaceRepository placeRepository;
    private final ReviewRepository reviewRepository;
    private final SeoulPublicDataService seoulPublicDataService;

    // 서울 주요 지역 목록
    // 지역명은 공공 API 조회 키라서 서울시 공식 장소명과 한 글자라도 다르면 데이터가 안 내려온다.
    // 이름·좌표 모두 서울시 실시간 도시데이터 장소 목록 기준으로 맞춰둔다.
    private static final List<SeoulArea> SEOUL_AREAS = List.of(
            new SeoulArea("강남 MICE 관광특구", 37.51100, 127.06006),
            new SeoulArea("홍대입구역(2호선)", 37.55676, 126.92301),
            new SeoulArea("성수카페거리", 37.54297, 127.05660),
            new SeoulArea("명동 관광특구", 37.56415, 126.98185),
            new SeoulArea("이태원 관광특구", 37.53444, 126.99437),
            new SeoulArea("잠실 관광특구", 37.51648, 127.11527),
            new SeoulArea("신촌·이대역", 37.55704, 126.93897),
            new SeoulArea("인사동", 37.57386, 126.98606),
            new SeoulArea("동대문 관광특구", 37.56731, 127.01102),
            new SeoulArea("압구정로데오거리", 37.52550, 127.03873),
            new SeoulArea("북촌한옥마을", 37.58224, 126.98400),
            new SeoulArea("광화문·덕수궁", 37.57093, 126.97719),
            new SeoulArea("서울역", 37.55659, 126.97303),
            new SeoulArea("고속터미널역", 37.50481, 127.00586),
            new SeoulArea("건대입구역", 37.53997, 127.06820)
    );

    /**
     * 사용자 위치 기반 근처 지역의 혼잡도 조회
     * - 유저 리뷰 72h 이내 3개 이상: USER 데이터 사용
     * - 그 외: 서울시 공공 API 사용
     */
    public CongestionResponse getNearbyCongestion(double latitude, double longitude, int radius) {
        LocalDateTime since = LocalDateTime.now().minusHours(72);

        List<CongestionResponse.AreaItem> areas = SEOUL_AREAS.stream()
                .filter(area -> calculateDistance(latitude, longitude, area.latitude(), area.longitude()) <= radius)
                .map(area -> buildAreaItem(area, since))
                .filter(Objects::nonNull)
                .toList();

        return new CongestionResponse(areas);
    }

    private CongestionResponse.AreaItem buildAreaItem(SeoulArea area, LocalDateTime since) {
        // 지역 근처 장소들의 72h 리뷰 집계
        List<Place> nearbyPlaces = placeRepository.findNearbyAll(area.latitude(), area.longitude(), PLACE_SEARCH_RADIUS);
        List<Long> placeIds = nearbyPlaces.stream().map(Place::getId).toList();

        if (!placeIds.isEmpty()) {
            long reviewCount = reviewRepository.countByPlaceIdsAndCreatedAtAfter(placeIds, since);

            if (reviewCount >= USER_REVIEW_THRESHOLD) {
                List<Review> reviews = reviewRepository.findByPlaceIdsAndCreatedAtAfter(placeIds, since);
                String congestionLevel = aggregateCongestion(reviews);
                return new CongestionResponse.AreaItem(area.name(), congestionLevel, null, "USER");
            }
        }

        // 공공 API 사용
        SeoulCityDataResponse.Row row = seoulPublicDataService.fetchPopulation(area.name());
        if (row == null) return null;

        String congestionLevel = mapPublicCongestion(row.areaCongestLvl());
        int population = average(row.areaPpltnMin(), row.areaPpltnMax());
        return new CongestionResponse.AreaItem(area.name(), congestionLevel, population, "PUBLIC");
    }

    // 리뷰 CongestionLevel 다수결로 대표값 결정 후 문자열 매핑
    private String aggregateCongestion(List<Review> reviews) {
        Map<CongestionLevel, Long> counts = reviews.stream()
                .filter(r -> r.getCongestionLevel() != null)
                .collect(Collectors.groupingBy(Review::getCongestionLevel, Collectors.counting()));

        CongestionLevel dominant = counts.entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(CongestionLevel.LOW);

        return switch (dominant) {
            case HIGH -> "CROWDED";
            case MEDIUM -> "MODERATE";
            case LOW -> "LOW";
        };
    }

    // 서울시 공공 API 혼잡도 문자열 → 응답값 매핑
    private String mapPublicCongestion(String lvl) {
        if (lvl == null) return "LOW";
        return switch (lvl) {
            case "붐빔" -> "CROWDED";
            case "보통" -> "MODERATE";
            default -> "LOW"; // 여유, 한산
        };
    }

    private int average(Integer min, Integer max) {
        if (min == null && max == null) return 0;
        if (min == null) return max;
        if (max == null) return min;
        return (min + max) / 2;
    }

    // Haversine 공식으로 두 좌표 간 거리(m) 계산
    private int calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int EARTH_RADIUS = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return (int) Math.round(EARTH_RADIUS * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a)));
    }

    private record SeoulArea(String name, double latitude, double longitude) {}
}
