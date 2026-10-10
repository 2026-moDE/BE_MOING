package com.moing.backend.domain.congestion.service;

import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 지역 목록 자동 동기화 트리거 테스트
 */
class SeoulAreaSyncSchedulerTest {

    private SeoulAreaSyncService seoulAreaSyncService;
    private SeoulAreaSyncScheduler seoulAreaSyncScheduler;

    @BeforeEach
    void setUp() {
        seoulAreaSyncService = mock(SeoulAreaSyncService.class);
        seoulAreaSyncScheduler = new SeoulAreaSyncScheduler(seoulAreaSyncService);
        ReflectionTestUtils.setField(seoulAreaSyncScheduler, "syncOnStartup", true);
    }

    @Test
    @DisplayName("기동 시 지역 목록을 받아온다")
    void 기동_시_동기화한다() {
        seoulAreaSyncScheduler.syncOnStartup();

        verify(seoulAreaSyncService).sync();
    }

    @Test
    @DisplayName("토글을 끄면 기동 시 외부 호출을 하지 않는다")
    void 토글을_끄면_호출하지_않는다() {
        ReflectionTestUtils.setField(seoulAreaSyncScheduler, "syncOnStartup", false);

        seoulAreaSyncScheduler.syncOnStartup();

        verify(seoulAreaSyncService, never()).sync();
    }

    @Test
    @DisplayName("서울시 장애로 실패해도 기동을 막지 않는다")
    void 실패해도_예외를_던지지_않는다() {
        when(seoulAreaSyncService.sync()).thenThrow(new CustomException(ErrorCode.EXTERNAL_API_ERROR));

        assertThatCode(() -> seoulAreaSyncScheduler.syncOnStartup()).doesNotThrowAnyException();
        assertThatCode(() -> seoulAreaSyncScheduler.syncWeekly()).doesNotThrowAnyException();
    }
}
