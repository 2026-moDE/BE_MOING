package com.moing.backend.domain.place.service;

import com.moing.backend.domain.place.dto.LocationVerifyResponse;
import com.moing.backend.domain.place.dto.NaverLocalResponse;
import com.moing.backend.domain.place.dto.PlaceDetailResponse;
import com.moing.backend.domain.place.dto.PlaceNearbyResponse;
import com.moing.backend.domain.place.repository.PlaceSubscriptionRepository;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import com.moing.backend.domain.place.entity.Place;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.review.entity.CongestionLevel;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;

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
    private final PlaceSubscriptionRepository placeSubscriptionRepository;
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
        LocalDateTime since = LocalDateTime.now().minusHours(72);

        // query 없으면 DB에서 반경 내 장소(리뷰 있는 버블)만 반환
        if (query == null || query.isBlank()) {
            List<PlaceNearbyResponse.PlaceItem> items = placeRepository.findNearby(latitude, longitude, radius)
                    .stream()
                    .map(place -> toItem(place, since))
                    .toList();
            return new PlaceNearbyResponse(items);
        }

        NaverLocalResponse naverResult = naverSearchService.search(query);

        if (naverResult == null || naverResult.items() == null) {
            return new PlaceNearbyResponse(List.of());
        }

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
                .findTopWithImageByPlaceId(place.getId(), since, PageRequest.of(0, 1))
                .stream().findFirst()
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

    /**
     * 장소 상세 조회
     * Place 정보, 구독 여부, 최신 혼잡도(72h), 리뷰 수(72h)를 조합해 반환한다.
     */
    public PlaceDetailResponse getPlaceDetail(Long userId, Long placeId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        LocalDateTime since = LocalDateTime.now().minusHours(72);

        boolean isSubscribed = placeSubscriptionRepository.existsByUserIdAndPlaceId(userId, placeId);

        CongestionLevel congestionLevel = reviewRepository
                .findTopByPlaceIdAndCreatedAtAfterOrderByCreatedAtDesc(placeId, since)
                .map(Review::getCongestionLevel)
                .orElse(null);

        long reviewCount = reviewRepository.countByPlaceIdAndCreatedAtAfter(placeId, since);

        return new PlaceDetailResponse(
                place.getId(),
                place.getName(),
                place.getAddress(),
                place.getCategory(),
                place.getBusinessHours(),
                isSubscribed,
                congestionLevel,
                reviewCount
        );
    }

    /**
     * 위치 인증
     * 사용자 좌표와 장소 좌표 간 거리를 계산하여 50m 이내면 인증 성공으로 반환한다.
     */
    public LocationVerifyResponse verifyLocation(Long placeId, double latitude, double longitude) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        int distance = calculateDistance(
                latitude, longitude,
                place.getLatitude().doubleValue(),
                place.getLongitude().doubleValue()
        );

        return new LocationVerifyResponse(distance <= 50, distance);
    }

    // Haversine 공식으로 두 좌표 간 거리(m) 반환
    private int calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int EARTH_RADIUS = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return (int) Math.round(EARTH_RADIUS * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a)));
    }

    // Haversine 공식을 이용한 거리 계산 및 반경 필터링
    private boolean isWithinRadius(double userLat, double userLng,
                                   double itemLat, double itemLng, int radius) {
        return calculateDistance(userLat, userLng, itemLat, itemLng) <= radius;
    }
}