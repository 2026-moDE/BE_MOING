package com.moing.backend.domain.congestion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 서울시 지역 목록을 자동으로 맞춘다.
 *
 * <p>목록 조회는 인증키가 필요 없는 호출 5번(수 KB)이라 호출 한도를 신경 쓸 필요가 없어
 * 서버가 뜰 때마다 받아온다. 지역 목록이 비어 있으면 혼잡도 조회가 전부 404가 되므로,
 * 배포 후 누군가 관리자 API를 눌러야 동작하는 상태를 만들지 않는다.
 *
 * <p>서울시가 지역을 추가·개명하는 경우를 대비해 주 1회 다시 맞춘다.
 * 재시작과 주간 동기화 사이에 목록이 바뀌어도 하루 안에 반영된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeoulAreaSyncScheduler {

    private final SeoulAreaSyncService seoulAreaSyncService;

    /**
     * 기동 시 동기화 여부. 테스트와 로컬 개발에서는 꺼서 외부 호출을 막는다.
     */
    @Value("${seoul.area-sync-on-startup:true}")
    private boolean syncOnStartup;

    /**
     * 웹 서버가 요청을 받기 시작한 뒤에 돈다. 길어도 수 초라 따로 비동기로 돌리지 않는다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        if (!syncOnStartup) {
            log.info("[SeoulAreaSync] 기동 시 동기화가 꺼져 있어 건너뛴다");
            return;
        }
        sync("기동");
    }

    @Scheduled(cron = "0 0 4 * * MON", zone = "Asia/Seoul")
    public void syncWeekly() {
        sync("주간");
    }

    private void sync(String trigger) {
        try {
            seoulAreaSyncService.sync();
        } catch (RuntimeException e) {
            // 서울시 쪽 장애로 실패해도 기존 목록이 남아 있어 조회는 계속 된다.
            // 목록이 아직 비어 있다면 관리자 API로 수동 동기화할 수 있다.
            log.warn("[SeoulAreaSync] {} 동기화 실패: {}", trigger, e.getMessage());
        }
    }
}
