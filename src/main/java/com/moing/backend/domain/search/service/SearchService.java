package com.moing.backend.domain.search.service;

import com.moing.backend.domain.place.dto.KakaoLocalResponse;
import com.moing.backend.domain.place.entity.Place;
import com.moing.backend.domain.place.entity.PlaceCategory;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.place.service.KakaoSearchService;
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
    private final KakaoSearchService kakaoSearchService;
    private final SearchHistoryRepository searchHistoryRepository;

    /**
     * 장소 검색
     * 카카오 검색 API 호출 후 내부 DB 매칭.
     * 결과에 72h 이내 혼잡도·대표 사진을 조합한다.
     */
    @Transactional
    public PlaceSearchResponse searchPlaces(Long userId, String keyword, Double latitude, Double longitude, Integer radius) {
        LocalDateTime since = LocalDateTime.now().minusHours(72);

        List<PlaceSearchResponse.PlaceItem> items;
        KakaoLocalResponse kakaoResult = kakaoSearchService.search(
                keyword,
                longitude != null ? longitude : null,
                latitude != null ? latitude : null,
                radius
        );

        if (kakaoResult == null || kakaoResult.documents() == null) {
            items = List.of();
        } else {
            items = kakaoResult.documents().stream()
                    .map(doc -> buildSearchItem(doc, since))
                    .toList();
        }

        return new PlaceSearchResponse(items);
    }

    /**
     * 검색 자동완성
     * 네이버 검색 결과에서 키워드로 시작하는 장소명 상위 10개를 반환한다.
     */
    public AutocompleteResponse autocomplete(String keyword) {
        KakaoLocalResponse kakaoResult = kakaoSearchService.search(keyword, null, null, null);

        if (kakaoResult == null || kakaoResult.documents() == null) {
            return new AutocompleteResponse(List.of());
        }

        List<AutocompleteResponse.Suggestion> suggestions = kakaoResult.documents().stream()
                .limit(10)
                .map(doc -> {
                    String name = doc.placeName();
                    String address = doc.roadAddressName() != null ? doc.roadAddressName() : doc.addressName();
                    String category = PlaceCategory.fromKakaoCategoryCode(doc.categoryGroupCode()).name();
                    return new AutocompleteResponse.Suggestion(null, name, address, category);
                })
                .toList();

        return new AutocompleteResponse(suggestions);
    }

    /**
     * 검색어 저장 (장소 선택 시 호출)
     */
    @Transactional
    public void saveSearchHistory(Long userId, String keyword) {
        searchHistoryRepository.save(new SearchHistory(userId, keyword));
    }

    /**
     * 최근 검색어 전체 삭제
     */
    @Transactional
    public void deleteAllSearchHistory(Long userId) {
        searchHistoryRepository.deleteAllByUserId(userId);
    }

    /**
     * 최근 검색어 개별 삭제 (같은 키워드 전체 삭제)
     */
    @Transactional
    public void deleteSearchHistoryByKeyword(Long userId, String keyword) {
        searchHistoryRepository.deleteAllByUserIdAndKeyword(userId, keyword);
    }

    /**
     * 최근 검색어 목록 조회 (최대 20건, 최신순)
     */
    public SearchHistoryResponse getSearchHistory(Long userId) {
        List<SearchHistoryResponse.HistoryItem> items = searchHistoryRepository
                .findRecentByUserId(userId, PageRequest.of(0, 5))
                .stream()
                .map(SearchHistoryResponse.HistoryItem::from)
                .toList();
        return new SearchHistoryResponse(items);
    }

    private PlaceSearchResponse.PlaceItem buildSearchItem(KakaoLocalResponse.Document doc, LocalDateTime since) {
        String name = doc.placeName();
        String address = doc.roadAddressName() != null ? doc.roadAddressName() : doc.addressName();
        PlaceCategory category = PlaceCategory.fromKakaoCategoryCode(doc.categoryGroupCode());
        double lat = Double.parseDouble(doc.y());
        double lng = Double.parseDouble(doc.x());

        // DB에 있으면 혼잡도·썸네일 포함, 없으면 upsert 후 반환
        Optional<Place> existing = placeRepository.findByNameAndIsActiveTrue(name);
        if (existing.isPresent()) {
            return toSearchItem(existing.get(), since);
        }

        BigDecimal latitude = new BigDecimal(doc.y());
        BigDecimal longitude = new BigDecimal(doc.x());

        Place saved = placeRepository.save(Place.builder()
                .name(name)
                .address(address)
                .latitude(latitude)
                .longitude(longitude)
                .category(category)
                .source("KAKAO")
                .build());

        return new PlaceSearchResponse.PlaceItem(saved.getId(), name, address, category, lat, lng, null, null);
    }

    private PlaceSearchResponse.PlaceItem toSearchItem(Place place, LocalDateTime since) {
        var congestionLevel = reviewRepository
                .findTopByPlaceIdAndIsBlindedFalseAndCreatedAtAfterOrderByCreatedAtDesc(place.getId(), since)
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
