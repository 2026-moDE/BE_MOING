package com.moing.backend.domain.search.service;

import com.moing.backend.domain.place.dto.KakaoLocalResponse;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.place.service.KakaoSearchService;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.domain.search.dto.AutocompleteResponse;
import com.moing.backend.domain.search.repository.SearchHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 검색 자동완성 테스트
 * 카카오는 좌표가 없으면 전국 정확도순을 주므로, 좌표 전달과 ETC 후순위 배치를 검증한다.
 */
class SearchServiceTest {

    private KakaoSearchService kakaoSearchService;
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        kakaoSearchService = mock(KakaoSearchService.class);
        searchService = new SearchService(
                mock(PlaceRepository.class),
                mock(ReviewRepository.class),
                kakaoSearchService,
                mock(SearchHistoryRepository.class));
    }

    @Test
    @DisplayName("좌표를 받으면 카카오에 그대로 넘겨 거리순 정렬을 받는다")
    void 좌표를_카카오에_전달한다() {
        when(kakaoSearchService.search(any(), any(), any(), any()))
                .thenReturn(new KakaoLocalResponse(List.of()));

        searchService.autocomplete("바나", 37.4979, 127.0276);

        // KakaoSearchService.search(keyword, longitude, latitude, radius) 순서
        verify(kakaoSearchService).search("바나", 127.0276, 37.4979, null);
    }

    @Test
    @DisplayName("좌표가 없어도 호출은 되고 카카오 기본 정렬을 따른다")
    void 좌표가_없어도_조회된다() {
        when(kakaoSearchService.search(any(), any(), any(), any()))
                .thenReturn(new KakaoLocalResponse(List.of(doc("바나프레소", "CE7"))));

        AutocompleteResponse response = searchService.autocomplete("바나", null, null);

        verify(kakaoSearchService).search(eq("바나"), eq(null), eq(null), eq(null));
        assertThat(response.suggestions()).hasSize(1);
    }

    @Test
    @DisplayName("ETC 장소는 목록 뒤로 밀린다")
    void ETC는_뒤로_밀린다() {
        when(kakaoSearchService.search(any(), any(), any(), any()))
                .thenReturn(new KakaoLocalResponse(List.of(
                        doc("바나인테리어", null),      // ETC
                        doc("바나프레소 강남역점", "CE7"),
                        doc("바나요가", null),          // ETC
                        doc("카츠오바나", "FD6"))));

        List<AutocompleteResponse.Suggestion> suggestions =
                searchService.autocomplete("바나", 37.4979, 127.0276).suggestions();

        assertThat(suggestions).extracting(AutocompleteResponse.Suggestion::name)
                .containsExactly("바나프레소 강남역점", "카츠오바나", "바나인테리어", "바나요가");
    }

    @Test
    @DisplayName("ETC가 상위를 채워도 뒤쪽 카페가 10개 안으로 올라온다")
    void 정렬_후_자르므로_카페가_살아남는다() {
        List<KakaoLocalResponse.Document> documents = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            documents.add(doc("바나잡화점" + i, null));   // ETC 12개가 앞을 채운다
        }
        documents.add(doc("바나프레소 역삼점", "CE7"));    // 13번째

        when(kakaoSearchService.search(any(), any(), any(), any()))
                .thenReturn(new KakaoLocalResponse(documents));

        List<AutocompleteResponse.Suggestion> suggestions =
                searchService.autocomplete("바나", 37.4979, 127.0276).suggestions();

        // 자른 뒤에 정렬했다면 13번째 카페는 잘려 나갔을 것이다
        assertThat(suggestions).hasSize(10);
        assertThat(suggestions.get(0).name()).isEqualTo("바나프레소 역삼점");
    }

    private KakaoLocalResponse.Document doc(String placeName, String categoryGroupCode) {
        return new KakaoLocalResponse.Document(
                placeName, "지번주소", "서울 강남구 테헤란로 1",
                categoryGroupCode, "카테고리명", "127.0276", "37.4979");
    }
}
