package com.moing.backend.domain.search.service;

import com.moing.backend.domain.place.dto.NaverLocalResponse;
import com.moing.backend.domain.place.entity.Place;
import com.moing.backend.domain.place.entity.PlaceCategory;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.place.service.NaverSearchService;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.domain.search.dto.AutocompleteResponse;
import com.moing.backend.domain.search.dto.PlaceSearchResponse;
import com.moing.backend.domain.search.dto.SearchHistoryResponse;
import com.moing.backend.domain.search.entity.SearchHistory;
import com.moing.backend.domain.search.repository.SearchHistoryRepository;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private final PlaceRepository placeRepository;
    private final ReviewRepository reviewRepository;
    private final NaverSearchService naverSearchService;
    private final SearchHistoryRepository searchHistoryRepository;

    /**
     * 장소 검색
     * (추후) keyword가 #으로 시작하면 quick_tag 기반 리뷰 검색,
     * 네이버 검색 API 호출 후 내부 DB 매칭.
     * 결과에 72h 이내 혼잡도·대표 사진을 조합하고 검색어를 search_history에 저장한다.
     */
    @Transactional
    public PlaceSearchResponse searchPlaces(Long userId, String keyword, Double latitude, Double longitude, Integer radius, boolean saveHistory) {
        if (saveHistory) {
            searchHistoryRepository.save(new SearchHistory(userId, keyword));
        }

        LocalDateTime since = LocalDateTime.now().minusHours(72);

        List<PlaceSearchResponse.PlaceItem> items;
//        if (keyword.startsWith("#")) {
//            String tag = keyword.substring(1);
//            List<Long> placeIds = reviewRepository.findPlaceIdsByQuickTag(tag);
//            List<Place> places = placeIds.isEmpty() ? List.of() : placeRepository.findByIdInAndIsActiveTrue(placeIds);
//            items = places.stream().map(place -> toSearchItem(place, since)).toList();
//        } else {
            NaverLocalResponse naverResult = naverSearchService.search(keyword);
            if (naverResult == null || naverResult.items() == null) {
                items = List.of();
            } else {
                items = naverResult.items().stream()
                        .map(naverItem -> buildSearchItem(naverItem, since))
                        .filter(item -> {
                            if (latitude == null || longitude == null || radius == null) return true;
                            int distance = calculateDistance(latitude, longitude, item.latitude(), item.longitude());
                            return distance <= radius;
                        })
                        .toList();
            }
//        }

        return new PlaceSearchResponse(items);
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

    /**
     * 검색 자동완성
     * 네이버 검색 결과에서 키워드로 시작하는 장소명 상위 10개를 반환한다.
     */
    public AutocompleteResponse autocomplete(String keyword) {
        NaverLocalResponse naverResult = naverSearchService.search(keyword);

        if (naverResult == null || naverResult.items() == null) {
            return new AutocompleteResponse(List.of());
        }

        List<AutocompleteResponse.Suggestion> suggestions = naverResult.items().stream()
                .limit(10)
                .map(item -> {
                    String name = item.cleanTitle();
                    String address = (item.roadAddress() != null && !item.roadAddress().isBlank())
                            ? item.roadAddress() : item.address();
                    String category = PlaceCategory.fromNaverCategory(item.category()).name();
                    return new AutocompleteResponse.Suggestion(null, name, address, category);
                })
                .toList();

        return new AutocompleteResponse(suggestions);
    }

    /**
     * 최근 검색어 전체 삭제
     */
    @Transactional
    public void deleteAllSearchHistory(Long userId) {
        searchHistoryRepository.deleteAllByUserId(userId);
    }

    /**
     * 최근 검색어 개별 삭제
     */
    @Transactional
    public void deleteSearchHistory(Long id, Long userId) {
        if (!searchHistoryRepository.existsByIdAndUserId(id, userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        searchHistoryRepository.deleteById(id);
    }

    /**
     * 최근 검색어 목록 조회 (최대 20건, 최신순)
     */
    public SearchHistoryResponse getSearchHistory(Long userId) {
        List<SearchHistoryResponse.HistoryItem> items = searchHistoryRepository
                .findRecentByUserId(userId, PageRequest.of(0, 20))
                .stream()
                .map(SearchHistoryResponse.HistoryItem::from)
                .toList();
        return new SearchHistoryResponse(items);
    }

    private PlaceSearchResponse.PlaceItem buildSearchItem(NaverLocalResponse.Item naverItem, LocalDateTime since) {
        String name = naverItem.cleanTitle();
        String address = naverItem.roadAddress() != null && !naverItem.roadAddress().isBlank()
                ? naverItem.roadAddress() : naverItem.address();
        PlaceCategory category = PlaceCategory.fromNaverCategory(naverItem.category());
        double lat = Double.parseDouble(naverItem.mapy()) / 10_000_000.0;
        double lng = Double.parseDouble(naverItem.mapx()) / 10_000_000.0;

        // DB에 있으면 혼잡도·썸네일 포함, 없으면 upsert 후 반환
        Optional<Place> existing = placeRepository.findByNameAndIsActiveTrue(name);
        if (existing.isPresent()) {
            return toSearchItem(existing.get(), since);
        }

        BigDecimal latitude = new BigDecimal(naverItem.mapy()).movePointLeft(7);
        BigDecimal longitude = new BigDecimal(naverItem.mapx()).movePointLeft(7);

        Place saved = placeRepository.save(Place.builder()
                .name(name)
                .address(address)
                .latitude(latitude)
                .longitude(longitude)
                .category(category)
                .source("NAVER")
                .build());

        return new PlaceSearchResponse.PlaceItem(saved.getId(), name, address, category, lat, lng, null, null);
    }

    private PlaceSearchResponse.PlaceItem toSearchItem(Place place, LocalDateTime since) {
        var congestionLevel = reviewRepository
                .findTopByPlaceIdAndCreatedAtAfterOrderByCreatedAtDesc(place.getId(), since)
                .map(Review::getCongestionLevel)
                .orElse(null);

        var thumbnailUrl = reviewRepository
                .findTopWithImageByPlaceId(place.getId(), since, PageRequest.of(0, 1))
                .stream().findFirst()
                .map(Review::getImageUrl)
                .orElse(null);

        return new PlaceSearchResponse.PlaceItem(
                place.getId(),
                place.getName(),
                place.getAddress(),
                place.getCategory(),
                place.getLatitude().doubleValue(),
                place.getLongitude().doubleValue(),
                congestionLevel,
                thumbnailUrl
        );
    }
}
