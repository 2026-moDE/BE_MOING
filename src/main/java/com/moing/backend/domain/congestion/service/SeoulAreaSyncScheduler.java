package com.moing.backend.domain.congestion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주 1회 서울시 지역 목록을 다시 맞춘다.
 *
 * <p>목록이 바뀌는 일은 드물어 트래픽이 적은 월요일 새벽에 한 번만 돈다.
 * 최초 적재는 관리자 API(POST /api/admin/seoul-areas/sync)로 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeoulAreaSyncScheduler {

    private final SeoulAreaSyncService seoulAreaSyncService;

    @Scheduled(cron = "0 0 4 * * MON", zone = "Asia/Seoul")
    public void syncSeoulAreas() {
        try {
            seoulAreaSyncService.sync();
        } catch (RuntimeException e) {
            // 서울시 쪽 장애로 실패해도 기존 목록이 남아 있어 조회는 계속 된다
            log.warn("[SeoulAreaSync] 주간 동기화 실패: {}", e.getMessage());
        }
    }
}
