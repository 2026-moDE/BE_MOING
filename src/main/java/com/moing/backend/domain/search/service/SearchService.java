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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 장소 검색
     * (추후) keyword가 #으로 시작하면 quick_tag 기반 리뷰 검색,
     * 네이버 검색 API 호출 후 내부 DB 매칭.
     * 결과에 72h 이내 혼잡도·대표 사진을 조합하고 검색어를 search_history에 저장한다.
     */
    public PlaceSearchResponse searchPlaces(Long userId, String keyword) {
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
                        .toList();
            }
//        }

        return new PlaceSearchResponse(items);
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

    private PlaceSearchResponse.PlaceItem buildSearchItem(NaverLocalResponse.Item naverItem, LocalDateTime since) {
        String name = naverItem.cleanTitle();
        String address = naverItem.roadAddress() != null ? naverItem.roadAddress() : naverItem.address();
        PlaceCategory category = PlaceCategory.fromNaverCategory(naverItem.category());

        // DB에 있는 장소면 혼잡도·썸네일 포함, 없으면 네이버 정보만 반환
        return placeRepository.findByNameAndIsActiveTrue(name)
                .map(place -> toSearchItem(place, since))
                .orElse(new PlaceSearchResponse.PlaceItem(null, name, address, category, null, null));
    }

    private PlaceSearchResponse.PlaceItem toSearchItem(Place place, LocalDateTime since) {
        var congestionLevel = reviewRepository
                .findTopByPlaceIdAndCreatedAtAfterOrderByCreatedAtDesc(place.getId(), since)
                .map(Review::getCongestionLevel)
                .orElse(null);

        var thumbnailUrl = reviewRepository
                .findTopWithImageByPlaceId(place.getId(), since)
                .map(Review::getImageUrl)
                .orElse(null);

        return new PlaceSearchResponse.PlaceItem(
                place.getId(),
                place.getName(),
                place.getAddress(),
                place.getCategory(),
                congestionLevel,
                thumbnailUrl
        );
    }
}
