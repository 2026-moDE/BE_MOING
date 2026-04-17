package com.moing.backend.domain.place.service;

import com.moing.backend.domain.place.dto.NaverLocalResponse;
import com.moing.backend.domain.place.dto.PlaceNearbyResponse;
import com.moing.backend.domain.place.entity.Place;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 장소 서비스
 * 네이버 지역 검색 결과와 내부 DB를 매칭하여 혼잡도·대표 사진을 조합한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final ReviewRepository reviewRepository;
    private final NaverSearchService naverSearchService;

    /**
     * 주변 장소 조회
     *
     * <ol>
     *   <li>네이버 지역 검색 API 호출</li>
     *   <li>결과를 WGS84로 변환 후 반경 필터링</li>
     *   <li>장소명으로 내부 DB 매칭 → 리뷰 데이터 조합</li>
     * </ol>
     *
     * @param latitude  사용자 위도 (WGS84)
     * @param longitude 사용자 경도 (WGS84)
     * @param radius    검색 반경 (m)
     * @param query     네이버 검색어 (예: "카페", "맛집")
     */
    // 주변 장소 조회 (네이버 검색 -> 좌표 변환 및 필터링 -> DB 매칭_
    public PlaceNearbyResponse getNearbyPlaces(double latitude, double longitude, int radius, String query) {
        NaverLocalResponse naverResult = naverSearchService.search(query);

        if (naverResult == null || naverResult.items() == null) {
            return new PlaceNearbyResponse(List.of());
        }

        // 최근 72시간 이내 데이터 추출 기준 시간
        LocalDateTime since = LocalDateTime.now().minusHours(72);

        List<PlaceNearbyResponse.PlaceItem> items = naverResult.items().stream()
                .map(item -> buildPlaceItem(item, latitude, longitude, radius, since))
                .filter(Objects::nonNull)
                .toList();

        return new PlaceNearbyResponse(items);
    }

    // Naver 검색 결과 아이템을 PlaceItem DTO로 변환
    private PlaceNearbyResponse.PlaceItem buildPlaceItem(
            NaverLocalResponse.Item naverItem,
            double userLat, double userLng, int radius,
            LocalDateTime since) {

        // 1. 네이버 좌표(10^7) -> WGS84 위경도로 직접 변환
        double itemLng = Double.parseDouble(naverItem.mapx()) / 10_000_000.0;
        double itemLat = Double.parseDouble(naverItem.mapy()) / 10_000_000.0;

        // 2. 반경 내 장소만 포함
        if (!isWithinRadius(userLat, userLng, itemLat, itemLng, radius)) {
            return null;
        }

        // 3. 장소명 기준 내부 DB 매칭
        String name = naverItem.cleanTitle();
        Optional<Place> dbPlace = placeRepository.findByNameAndIsActiveTrue(name);

        // DB에 존재하면 상세 정보 포함해서 반환
        if (dbPlace.isPresent()) {
            return toItem(dbPlace.get(), since);
        }

        // DB에 없으면 네이버 정보만 반환
        return new PlaceNearbyResponse.PlaceItem(null, name, itemLat, itemLng, null, null);
    }

    // DB 엔티티를 응답 DTO로 변환
    private PlaceNearbyResponse.PlaceItem toItem(Place place, LocalDateTime since) {
        // 최신 혼잡도 레벨 조회 (72시간 이내)
        var congestionLevel = reviewRepository
                .findTopByPlaceIdAndCreatedAtAfterOrderByCreatedAtDesc(place.getId(), since)
                .map(Review::getCongestionLevel)
                .orElse(null);

        // 대표 이미지 조회 (72시간 이내)
        var thumbnailUrl = reviewRepository
                .findTopWithImageByPlaceId(place.getId(), since)
                .map(Review::getImageUrl)
                .orElse(null);

        return new PlaceNearbyResponse.PlaceItem(
                place.getId(),
                place.getName(),
                place.getLatitude().doubleValue(),
                place.getLongitude().doubleValue(),
                congestionLevel,
                thumbnailUrl
        );
    }

    // Haversine 공식을 이용한 거리 계산 및 반경 필터링
    private boolean isWithinRadius(double userLat, double userLng,
                                   double itemLat, double itemLng, int radius) {
        final int EARTH_RADIUS = 6_371_000; // 지구 반지름 (m)
        double dLat = Math.toRadians(itemLat - userLat);
        double dLon = Math.toRadians(itemLng - userLng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(userLat)) * Math.cos(Math.toRadians(itemLat))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double dist = EARTH_RADIUS * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

        return dist <= radius;
    }
}