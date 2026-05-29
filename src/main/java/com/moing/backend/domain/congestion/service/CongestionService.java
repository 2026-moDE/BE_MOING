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

    // 서울 주요 지역 목록 (지역명은 서울 실시간 도시 데이터 API 명칭 사용)
    private static final List<SeoulArea> SEOUL_AREAS = List.of(
            new SeoulArea("강남 MICE 관광특구", 37.5172, 127.0473),
            new SeoulArea("홍대입구역", 37.5575, 126.9244),
            new SeoulArea("성수동", 37.5446, 127.0564),
            new SeoulArea("명동", 37.5636, 126.9847),
            new SeoulArea("이태원·한남동", 37.5347, 126.9943),
            new SeoulArea("잠실 MICE 관광특구", 37.5133, 127.1000),
            new SeoulArea("신촌·이대", 37.5555, 126.9369),
            new SeoulArea("인사동·익선동", 37.5745, 126.9856),
            new SeoulArea("동대문 관광특구", 37.5714, 127.0097),
            new SeoulArea("압구정로데오거리", 37.5273, 127.0287),
            new SeoulArea("북촌한옥마을", 37.5816, 126.9835),
            new SeoulArea("광화문·덕수궁", 37.5703, 126.9768),
            new SeoulArea("서울역", 37.5546, 126.9707),
            new SeoulArea("고속터미널", 37.5051, 127.0047),
            new SeoulArea("건대입구역", 37.5403, 127.0697)
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
        List<Place> nearbyPlaces = placeRepository.findNearby(area.latitude(), area.longitude(), PLACE_SEARCH_RADIUS);
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
