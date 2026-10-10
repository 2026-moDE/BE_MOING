package com.moing.backend.domain.congestion.service;

import com.moing.backend.domain.congestion.dto.SeoulAreaSyncResponse;
import com.moing.backend.domain.congestion.entity.SeoulArea;
import com.moing.backend.domain.congestion.repository.SeoulAreaRepository;
import com.moing.backend.global.infra.SeoulHotspotClient;
import com.moing.backend.global.infra.SeoulHotspotResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 서울시 지역 목록 동기화 테스트
 */
class SeoulAreaSyncServiceTest {

    private SeoulHotspotClient seoulHotspotClient;
    private SeoulAreaRepository seoulAreaRepository;
    private SeoulAreaSyncService seoulAreaSyncService;

    @BeforeEach
    void setUp() {
        seoulHotspotClient = mock(SeoulHotspotClient.class);
        seoulAreaRepository = mock(SeoulAreaRepository.class);
        seoulAreaSyncService = new SeoulAreaSyncService(seoulHotspotClient, seoulAreaRepository);

        when(seoulAreaRepository.findAll()).thenReturn(List.of());
        when(seoulAreaRepository.findByAreaNmNotIn(anyList())).thenReturn(List.of());
    }

    @Test
    @DisplayName("처음 동기화하면 받은 지역을 전부 저장한다")
    void 신규_지역을_저장한다() {
        when(seoulHotspotClient.fetchAllAreas()).thenReturn(List.of(
                row("성수카페거리", "발달상권", 37.54297, 127.05660),
                row("서울역", "인구밀집지역", 37.55659, 126.97303)));

        SeoulAreaSyncResponse response = seoulAreaSyncService.sync();

        assertThat(response.createdCount()).isEqualTo(2);
        assertThat(response.updatedCount()).isZero();
        assertThat(response.fetchedCount()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SeoulArea>> captor = ArgumentCaptor.forClass(List.class);
        verify(seoulAreaRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(SeoulArea::getAreaNm)
                .containsExactly("성수카페거리", "서울역");
    }

    @Test
    @DisplayName("이미 있는 지역은 좌표·분류를 덮어쓴다")
    void 기존_지역을_갱신한다() {
        SeoulArea stored = SeoulArea.of("성수카페거리", "인구밀집지역", 37.0, 127.0, LocalDateTime.of(2026, 1, 1, 0, 0));
        when(seoulAreaRepository.findAll()).thenReturn(List.of(stored));
        when(seoulHotspotClient.fetchAllAreas())
                .thenReturn(List.of(row("성수카페거리", "발달상권", 37.54297, 127.05660)));

        SeoulAreaSyncResponse response = seoulAreaSyncService.sync();

        assertThat(response.createdCount()).isZero();
        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(stored.getCategory()).isEqualTo("발달상권");
        assertThat(stored.getLatitude().doubleValue()).isEqualTo(37.54297);
    }

    @Test
    @DisplayName("서울시 목록에서 빠진 지역은 삭제한다")
    void 사라진_지역을_삭제한다() {
        SeoulArea removed = SeoulArea.of("없어진지역", "공원", 37.5, 127.0, LocalDateTime.of(2026, 1, 1, 0, 0));
        when(seoulAreaRepository.findByAreaNmNotIn(anyList())).thenReturn(List.of(removed));
        when(seoulHotspotClient.fetchAllAreas())
                .thenReturn(List.of(row("성수카페거리", "발달상권", 37.54297, 127.05660)));

        SeoulAreaSyncResponse response = seoulAreaSyncService.sync();

        assertThat(response.removedCount()).isEqualTo(1);
        verify(seoulAreaRepository).deleteAll(List.of(removed));
    }

    @Test
    @DisplayName("좌표가 없거나 지역명이 중복인 항목은 건너뛴다")
    void 쓸_수_없는_항목은_건너뛴다() {
        when(seoulHotspotClient.fetchAllAreas()).thenReturn(List.of(
                row("성수카페거리", "발달상권", 37.54297, 127.05660),
                row("성수카페거리", "인구밀집지역", 37.54297, 127.05660),   // 중복 — unique 제약에 걸린다
                new SeoulHotspotResponse.Row("좌표없는지역", "공원", null, null),
                new SeoulHotspotResponse.Row(null, "공원", 37.5, 127.0)));

        SeoulAreaSyncResponse response = seoulAreaSyncService.sync();

        assertThat(response.createdCount()).isEqualTo(1);
        assertThat(response.skippedCount()).isEqualTo(3);
    }

    private SeoulHotspotResponse.Row row(String areaNm, String category, double latitude, double longitude) {
        return new SeoulHotspotResponse.Row(areaNm, category, latitude, longitude);
    }
}
