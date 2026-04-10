package com.moing.backend.domain.place.service;

import com.moing.backend.domain.place.dto.PlaceNearbyResponse;
import com.moing.backend.domain.place.entity.Place;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 장소 서비스
 * 반경 내 장소 조회 및 리뷰 기반 혼잡도·대표 사진 조합을 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final ReviewRepository reviewRepository;

    // 현재 위치(latitude, longitude) 기준 반경(radius)m 내 활성 장소를 반환
    public PlaceNearbyResponse getNearbyPlaces(double latitude, double longitude, int radius) {
        List<Place> places = placeRepository.findNearby(latitude, longitude, radius);
        LocalDateTime since = LocalDateTime.now().minusHours(72);

        List<PlaceNearbyResponse.PlaceItem> items = places.stream()
                .map(place -> toItem(place, since))
                .toList();

        return new PlaceNearbyResponse(items);
    }

    // 장소 하나를 응답 DTO로 변환
    // 혼잡도: 72h 이내 가장 최근 리뷰의 congestion_level (없으면 null)
    // 대표 사진: 72h 이내 helpful_count 가장 높은 리뷰의 image_url (없으면 null)
    private PlaceNearbyResponse.PlaceItem toItem(Place place, LocalDateTime since) {
        var congestionLevel = reviewRepository
                .findTopByPlaceIdAndCreatedAtAfterOrderByCreatedAtDesc(place.getId(), since)
                .map(Review::getCongestionLevel)
                .orElse(null);

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
}
