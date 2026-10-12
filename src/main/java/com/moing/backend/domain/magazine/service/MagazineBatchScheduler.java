package com.moing.backend.domain.magazine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매거진 묶음 생성을 하루 1회 돌린다.
 *
 * <p>서비스와 분리한 이유는 {@code @Transactional}이 프록시로 걸리기 때문이다.
 * 같은 빈 안에서 스케줄 메서드가 생성 메서드를 부르면 프록시를 타지 않아 트랜잭션이
 * 적용되지 않고, 중간에 실패하면 묶음이 지워진 상태로 남는다(빈 바텀시트).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MagazineBatchScheduler {

    private final MagazineBatchService magazineBatchService;

    /** 매일 새벽 4시(KST). 사용자가 가장 적은 시간대라 다시 만드는 사이의 빈 구간이 덜 보인다 */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void generateDaily() {
        try {
            magazineBatchService.generate();
        } catch (RuntimeException e) {
            // 트랜잭션이 되돌아가 어제 묶음이 그대로 남는다. 다음 날 다시 시도한다.
            log.warn("[Magazine] 묶음 생성 실패: {}", e.getMessage(), e);
        }
    }
}
