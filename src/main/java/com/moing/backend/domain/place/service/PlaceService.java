package com.moing.backend.domain.place.service;

import com.moing.backend.domain.place.dto.LocationVerifyResponse;
import com.moing.backend.domain.place.dto.KakaoLocalResponse;
import com.moing.backend.domain.place.dto.PlaceDetailResponse;
import com.moing.backend.domain.place.dto.PlaceNearbyResponse;
import com.moing.backend.domain.place.dto.SubscribeResponse;
import com.moing.backend.domain.place.entity.PlaceSubscription;
import com.moing.backend.domain.place.entity.PlaceCongestionCache;
import com.moing.backend.domain.place.repository.PlaceCongestionCacheRepository;
import com.moing.backend.domain.place.repository.PlaceSubscriptionRepository;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import com.moing.backend.domain.place.entity.Place;
import com.moing.backend.domain.place.entity.PlaceCategory;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private final PlaceCongestionCacheRepository congestionCacheRepository;
    private final PlaceSubscriptionRepository placeSubscriptionRepository;
    private final KakaoSearchService kakaoSearchService;

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
    // 주변 장소 조회 (카카오 검색 -> DB 매칭)
    public PlaceNearbyResponse getNearbyPlaces(double latitude, double longitude, Integer radius, String query, String filter) {
        LocalDateTime since = LocalDateTime.now().minusHours(72);

        // query 없으면 DB에서 장소 버블 반환 (filter에 따라 분기)
        if (query == null || query.isBlank()) {
            List<Place> places = resolvePlaces(filter, latitude, longitude, radius, since);
            Map<Long, PlaceCongestionCache> cacheMap = loadCacheMap(places);
            List<PlaceNearbyResponse.PlaceItem> items = places.stream()
                    .map(place -> toItem(place, filter, since, cacheMap.get(place.getId())))
                    .filter(item -> item.thumbnailUrl() != null)
                    .toList();
            return new PlaceNearbyResponse(items);
        }

        KakaoLocalResponse kakaoResult = kakaoSearchService.search(query, longitude, latitude, radius);

        if (kakaoResult == null || kakaoResult.documents() == null) {
            return new PlaceNearbyResponse(List.of());
        }

        List<PlaceNearbyResponse.PlaceItem> items = kakaoResult.documents().stream()
                .map(doc -> buildPlaceItemFromKakao(doc, filter, since))
                .toList();

        return new PlaceNearbyResponse(items);
    }

    // filter 값에 따라 장소 목록 조회
    private List<Place> resolvePlaces(String filter, double latitude, double longitude, Integer radius, LocalDateTime since) {
        return switch (filter) {
            case "current" -> (radius == null)
                    ? placeRepository.findAllWithRecentReviews(since)
                    : placeRepository.findNearby(latitude, longitude, radius, since);
            case "archived" -> (radius == null)
                    ? placeRepository.findAllWithArchivedReviews(since)
                    : placeRepository.findNearbyWithArchivedReviews(latitude, longitude, radius, since);
            default -> (radius == null)  // "all"
                    ? placeRepository.findAllByIsActiveTrue()
                    : placeRepository.findNearbyAll(latitude, longitude, radius);
        };
    }

    // 카카오 검색 결과를 PlaceItem DTO로 변환
    private PlaceNearbyResponse.PlaceItem buildPlaceItemFromKakao(
            KakaoLocalResponse.Document doc, String filter, LocalDateTime since) {

        double docLat = Double.parseDouble(doc.y());
        double docLng = Double.parseDouble(doc.x());
        String name = doc.placeName();

        Optional<Place> dbPlace = placeRepository.findByNameAndIsActiveTrue(name);

        if (dbPlace.isPresent()) {
            Place place = dbPlace.get();
            PlaceCongestionCache cache = congestionCacheRepository.findById(place.getId()).orElse(null);
            return toItem(place, filter, since, cache);
        }

        String address = doc.roadAddressName() != null ? doc.roadAddressName() : doc.addressName();
        PlaceCategory category = PlaceCategory.fromKakaoCategoryCode(doc.categoryGroupCode());
        return new PlaceNearbyResponse.PlaceItem(null, name, docLat, docLng, null, null, category, address);
    }

    // placeId 목록으로 혼잡도 캐시를 한 번에 로드
    private Map<Long, PlaceCongestionCache> loadCacheMap(List<Place> places) {
        List<Long> ids = places.stream().map(Place::getId).toList();
        return congestionCacheRepository.findAllById(ids)
                .stream()
                .collect(Collectors.toMap(PlaceCongestionCache::getPlaceId, c -> c));
    }

    // DB 엔티티를 응답 DTO로 변환 (filter에 따라 썸네일 조회 범위 결정)
    private PlaceNearbyResponse.PlaceItem toItem(Place place, String filter, LocalDateTime since, PlaceCongestionCache cache) {
        CongestionLevel congestionLevel = cache != null ? cache.getCongestionLevel() : null;

        var thumbnailUrl = switch (filter) {
            case "current" -> reviewRepository
                    .findTopWithImageByPlaceId(place.getId(), since, PageRequest.of(0, 1))
                    .stream().findFirst().map(Review::getImageUrl).orElse(null);
            case "archived" -> reviewRepository
                    .findTopWithArchivedImageByPlaceId(place.getId(), since, PageRequest.of(0, 1))
                    .stream().findFirst().map(Review::getImageUrl).orElse(null);
            default -> reviewRepository  // "all"
                    .findTopWithImageAllTimeByPlaceId(place.getId(), PageRequest.of(0, 1))
                    .stream().findFirst().map(Review::getImageUrl).orElse(null);
        };

        return new PlaceNearbyResponse.PlaceItem(
                place.getId(),
                place.getName(),
                place.getLatitude().doubleValue(),
                place.getLongitude().doubleValue(),
                congestionLevel,
                thumbnailUrl,
                place.getCategory(),
                place.getAddress()
        );
    }

    /**
     * 장소 검색 (네이버 API + 위치 기반 필터링)
     * latitude/longitude가 있으면 반경 내 결과만 반환, 없으면 전체 반환
     */
    public PlaceNearbyResponse searchPlaces(String keyword, Double latitude, Double longitude) {
        KakaoLocalResponse kakaoResult = kakaoSearchService.search(
                keyword,
                longitude != null ? longitude : null,
                latitude != null ? latitude : null,
                null
        );

        if (kakaoResult == null || kakaoResult.documents() == null) {
            return new PlaceNearbyResponse(List.of());
        }

        LocalDateTime since = LocalDateTime.now().minusHours(72);

        List<PlaceNearbyResponse.PlaceItem> items = kakaoResult.documents().stream()
                .map(doc -> {
                    double docLng = Double.parseDouble(doc.x());
                    double docLat = Double.parseDouble(doc.y());
                    String name = doc.placeName();

                    Optional<Place> dbPlace = placeRepository.findByNameAndIsActiveTrue(name);

                    if (dbPlace.isPresent()) {
                        Place place = dbPlace.get();
                        PlaceCongestionCache cache = congestionCacheRepository.findById(place.getId()).orElse(null);
                        return toItem(place, "all", since, cache);
                    }

                    String address = doc.roadAddressName() != null ? doc.roadAddressName() : doc.addressName();
                    PlaceCategory category = PlaceCategory.fromKakaoCategoryCode(doc.categoryGroupCode());
                    return new PlaceNearbyResponse.PlaceItem(null, name, docLat, docLng, null, null, category, address);
                })
                .toList();

        return new PlaceNearbyResponse(items);
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

        PlaceCongestionCache cache = congestionCacheRepository.findById(placeId).orElse(null);

        long reviewCount = reviewRepository.countByPlaceIdAndCreatedAtAfter(placeId, since);

        return new PlaceDetailResponse(
                place.getId(),
                place.getName(),
                place.getAddress(),
                place.getCategory(),
                place.getBusinessHours(),
                isSubscribed,
                cache != null ? cache.getCongestionLevel() : null,
                cache != null ? cache.getCongestionIndex() : null,
                cache != null ? cache.getReviewCount() : 0,
                cache != null ? cache.getUpdatedAt() : null,
                reviewCount
        );
    }

    /**
     * 위치 인증
     * 사용자 좌표와 장소 좌표 간 거리를 계산하여 200m 이내면 인증 성공으로 반환한다.
     */
    public LocationVerifyResponse verifyLocation(Long placeId, double latitude, double longitude) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        int distance = calculateDistance(
                latitude, longitude,
                place.getLatitude().doubleValue(),
                place.getLongitude().doubleValue()
        );

        return new LocationVerifyResponse(distance <= 200, distance);
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

    @Transactional
    public SubscribeResponse subscribe(Long userId, Long placeId) {
        placeRepository.findById(placeId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        if (placeSubscriptionRepository.existsByUserIdAndPlaceId(userId, placeId)) {
            throw new CustomException(ErrorCode.DUPLICATE);
        }

        placeSubscriptionRepository.save(PlaceSubscription.builder()
                .userId(userId)
                .placeId(placeId)
                .build());

        return new SubscribeResponse(true);
    }

    @Transactional
    public SubscribeResponse unsubscribe(Long userId, Long placeId) {
        placeRepository.findById(placeId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        if (!placeSubscriptionRepository.existsByUserIdAndPlaceId(userId, placeId)) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }

        placeSubscriptionRepository.deleteByUserIdAndPlaceId(userId, placeId);

        return new SubscribeResponse(false);
    }
}