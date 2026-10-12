package com.moing.backend.domain.congestion.service;

import com.moing.backend.domain.congestion.dto.CongestionResponse;
import com.moing.backend.domain.congestion.dto.SeoulAreaSearchResponse;
import com.moing.backend.domain.congestion.entity.SeoulArea;
import com.moing.backend.domain.congestion.repository.SeoulAreaRepository;
import com.moing.backend.domain.review.entity.CongestionLevel;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import com.moing.backend.global.infra.SeoulCityDataResponse;
import com.moing.backend.global.infra.SeoulPublicDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 지역 선택 화면용 검색 테스트.
 *
 * <p>선택 대상은 서울시가 실시간 인구를 내려주는 지역뿐이라, 검색이 {@code seoul_areas}
 * 밖으로 나가지 않는지도 함께 확인한다.
 */
class SeoulAreaSearchTest {

    private static final LocalDateTime SYNCED_AT = LocalDateTime.of(2026, 10, 10, 11, 15);

    private SeoulAreaRepository seoulAreaRepository;
    private SeoulPublicDataService seoulPublicDataService;
    private CongestionService congestionService;

    @BeforeEach
    void setUp() {
        seoulAreaRepository = mock(SeoulAreaRepository.class);
        seoulPublicDataService = mock(SeoulPublicDataService.class);
        congestionService = new CongestionService(seoulAreaRepository, seoulPublicDataService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("키워드가 없으면 선택 가능한 전체 지역을 내려준다")
    void 키워드가_없으면_전체_목록이다(String keyword) {
        when(seoulAreaRepository.findAllByOrderByAreaNmAsc())
                .thenReturn(List.of(area("강남역", "인구밀집지역"), area("성수카페거리", "발달상권")));

        assertThat(congestionService.searchAreas(keyword).areas())
                .extracting(SeoulAreaSearchResponse.AreaItem::areaName)
                .containsExactly("강남역", "성수카페거리");
    }

    @Test
    @DisplayName("지역명 부분 일치로 검색한다")
    void 부분_일치로_검색한다() {
        when(seoulAreaRepository.findByAreaNmContainingIgnoreCaseOrderByAreaNmAsc("성수"))
                .thenReturn(List.of(area("성수카페거리", "발달상권")));

        SeoulAreaSearchResponse.AreaItem item = congestionService.searchAreas("성수").areas().get(0);

        assertThat(item.areaName()).isEqualTo("성수카페거리");
        assertThat(item.category()).isEqualTo("발달상권");
        // 선택한 지역의 혼잡도를 좌표로 바로 조회하므로 좌표가 빠지면 안 된다
        assertThat(item.latitude()).isEqualTo(37.54297);
        assertThat(item.longitude()).isEqualTo(127.05660);
    }

    @Test
    @DisplayName("앞뒤 공백은 떼고 검색한다")
    void 공백을_떼고_검색한다() {
        congestionService.searchAreas("  강남역  ");

        verify(seoulAreaRepository).findByAreaNmContainingIgnoreCaseOrderByAreaNmAsc("강남역");
    }

    @Test
    @DisplayName("영문 지역명은 대소문자를 가리지 않는다")
    void 영문은_대소문자를_무시한다() {
        when(seoulAreaRepository.findByAreaNmContainingIgnoreCaseOrderByAreaNmAsc("ddp"))
                .thenReturn(List.of(area("DDP(동대문디자인플라자)", "발달상권")));

        assertThat(congestionService.searchAreas("ddp").areas()).hasSize(1);
    }

    @Test
    @DisplayName("초성이 섞이면 정규식으로 검색한다")
    void 초성은_정규식으로_검색한다() {
        // "ㅎㄷ" → 초성 ㅎ·ㄷ으로 시작하는 음절 범위 두 개. 부분 일치로는 홍대를 못 잡는다
        congestionService.searchAreas("ㅎㄷ");

        verify(seoulAreaRepository).findByAreaNmRegex("[하-힣][다-딯]");
        verify(seoulAreaRepository, never())
                .findByAreaNmContainingIgnoreCaseOrderByAreaNmAsc(anyString());
    }

    @Test
    @DisplayName("지역 검색은 서울시 API를 호출하지 않는다")
    void 외부_API를_호출하지_않는다() {
        congestionService.searchAreas("강남");

        verify(seoulPublicDataService, never()).fetchPopulation(anyString());
    }

    @Test
    @DisplayName("고른 지역은 좌표가 아니라 id로 조회한다")
    void 고른_지역을_id로_조회한다() {
        when(seoulAreaRepository.findById(7L))
                .thenReturn(Optional.of(area("동대문 관광특구", "관광특구")));
        when(seoulPublicDataService.fetchPopulation("동대문 관광특구"))
                .thenReturn(new SeoulCityDataResponse.Row(
                        "동대문 관광특구", "POI003", "붐빔", "사람이 많아 붐벼요.",
                        16000, 18000, SYNCED_AT, "N", null));

        CongestionResponse response = congestionService.getAreaCongestion(7L);

        // 74m 옆 DDP가 아니라 고른 지역이 그대로 나와야 한다
        assertThat(response.areaName()).isEqualTo("동대문 관광특구");
        assertThat(response.congestionLevel()).isEqualTo(CongestionLevel.VERY_CROWDED);
        // 좌표로 최근접을 다시 찾지 않는다
        verify(seoulAreaRepository, never())
                .findNearest(anyDouble(), anyDouble(), anyInt(), any(Pageable.class));
    }

    @Test
    @DisplayName("없는 지역 id는 404로 끊는다")
    void 없는_지역은_404다() {
        when(seoulAreaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> congestionService.getAreaCongestion(999L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.AREA_NOT_FOUND);
    }

    private SeoulArea area(String areaNm, String category) {
        return SeoulArea.of(areaNm, category, 37.54297, 127.05660, SYNCED_AT);
    }
}
