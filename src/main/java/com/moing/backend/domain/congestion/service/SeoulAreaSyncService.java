package com.moing.backend.domain.congestion.service;

import com.moing.backend.domain.congestion.dto.SeoulAreaSyncResponse;
import com.moing.backend.domain.congestion.entity.SeoulArea;
import com.moing.backend.domain.congestion.repository.SeoulAreaRepository;
import com.moing.backend.global.infra.SeoulHotspotClient;
import com.moing.backend.global.infra.SeoulHotspotResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 서울시 지역 목록을 seoul_areas 테이블에 채운다.
 *
 * <p>지역명·좌표는 거의 바뀌지 않으므로 바텀시트 요청마다 받아올 필요가 없다.
 * 관리자가 한 번 실행해 채워두고, 서울시가 지역을 추가·개명하는 경우를 대비해
 * {@code SeoulAreaSyncScheduler}가 주 1회 다시 맞춘다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeoulAreaSyncService {

    private final SeoulHotspotClient seoulHotspotClient;
    private final SeoulAreaRepository seoulAreaRepository;

    /**
     * 서울시 목록을 받아 테이블을 그 상태로 맞춘다 (추가·갱신·삭제).
     *
     * <p>목록 조회가 실패하면 {@code SeoulHotspotClient}가 예외를 던지므로
     * 기존 데이터는 손대지 않은 채 트랜잭션이 끝난다.
     */
    @Transactional
    public SeoulAreaSyncResponse sync() {
        List<SeoulHotspotResponse.Row> rows = seoulHotspotClient.fetchAllAreas();
        LocalDateTime now = LocalDateTime.now();

        Map<String, SeoulArea> stored = seoulAreaRepository.findAll().stream()
                .collect(Collectors.toMap(SeoulArea::getAreaNm, Function.identity()));

        Set<String> syncedNames = new HashSet<>();
        List<SeoulArea> created = new ArrayList<>();
        int updatedCount = 0;
        int skippedCount = 0;

        for (SeoulHotspotResponse.Row row : rows) {
            // 지역명이 분류를 넘나들며 중복될 일은 없지만, 중복이 오면 unique 제약에 걸려
            // 동기화 전체가 실패하므로 먼저 걸러낸다
            if (!row.isUsable() || !syncedNames.add(row.areaNm())) {
                skippedCount++;
                continue;
            }

            SeoulArea area = stored.get(row.areaNm());
            if (area == null) {
                created.add(SeoulArea.of(row.areaNm(), row.category(), row.latitude(), row.longitude(), now));
            } else {
                area.update(row.category(), row.latitude(), row.longitude(), now);
                updatedCount++;
            }
        }

        seoulAreaRepository.saveAll(created);

        // 서울시 목록에서 빠진 지역은 더 이상 실시간 인구 API가 답하지 않으므로 지운다.
        // 쓸 만한 항목이 하나도 없으면 NOT IN 조건이 전체 행에 걸려 테이블을 비워버리므로 건너뛴다.
        List<SeoulArea> removed = syncedNames.isEmpty()
                ? List.of()
                : seoulAreaRepository.findByAreaNmNotIn(List.copyOf(syncedNames));
        seoulAreaRepository.deleteAll(removed);

        log.info("[SeoulAreaSync] 수신 {}곳 · 신규 {} · 갱신 {} · 삭제 {} · 제외 {}",
                rows.size(), created.size(), updatedCount, removed.size(), skippedCount);

        return new SeoulAreaSyncResponse(
                rows.size(), created.size(), updatedCount, removed.size(), skippedCount, now);
    }
}
