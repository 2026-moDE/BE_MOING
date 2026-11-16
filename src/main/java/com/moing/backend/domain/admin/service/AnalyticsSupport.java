package com.moing.backend.domain.admin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/**
 * 서비스 지표 집계에 공통으로 쓰는 기준값.
 *
 * <p>지표는 사회혁신 어워드 제출 근거로 쓰이므로, 어떤 수를 "실사용자"로 세는지와
 * 어느 시간대로 날짜를 끊는지가 API마다 달라지면 안 된다. 그 기준을 한 곳에 둔다.
 */
@Component
public class AnalyticsSupport {

    /**
     * 리뷰가 지도에 노출되는 시간(h).
     *
     * <p>서비스 전반이 이 값으로 "현재 정보"를 끊는데 공용 상수가 없어 각 서비스가 72를
     * 직접 들고 있다({@code PlaceService}, {@code ReviewService}, {@code UserService} 등).
     * 지표 쪽에서 쓸 값에 이름을 붙여 두되, 기존 코드는 건드리지 않는다 —
     * 노출 기준이 바뀌면 이 상수와 그쪽을 함께 고쳐야 한다.
     */
    public static final int MAX_EXPOSURE_HOURS = 72;

    /** 날짜·시간 집계 기준 시간대. created_at은 UTC로 저장되므로 집계할 때 변환한다 */
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * UTC로 저장된 시각을 KST로 옮길 때 더하는 시간.
     *
     * <p>SQL에서 {@code AT TIME ZONE} 연쇄를 쓸 수 없어(H2 미지원) 오프셋을 더하는 방식인데,
     * 한국은 1988년 이후 일광 절약 시간제가 없어 +9로 고정해도 어긋나지 않는다.
     * 숫자를 박아두지 않고 시간대 규칙에서 꺼내 그 사실을 코드로 남긴다.
     */
    public static int kstOffsetHours() {
        return KST.getRules().getOffset(Instant.EPOCH).getTotalSeconds() / 3600;
    }

    /**
     * {@code daily_visible}이 하루를 대표하는 시각(KST).
     *
     * <p>노출 중인 리뷰 수는 시각에 따라 출렁이므로 하루에 한 시점을 정해 비교한다.
     * 저녁 외출 시간대가 서비스 사용이 가장 많아 이 시각을 택했다.
     */
    public static final int DAILY_SNAPSHOT_HOUR_KST = 21;

    /** {@code daily_visible}이 거슬러 보는 일수 */
    public static final int DAILY_VISIBLE_DAYS = 30;

    /**
     * 집계에서 제외할 내부 계정 id.
     *
     * <p>비어 있을 수 있는데 네이티브 SQL의 {@code NOT IN ()}은 빈 목록을 받으면 깨지므로,
     * 실제 id와 겹치지 않는 센티넬을 넣어 목록이 항상 1개 이상이게 만든다.
     */
    private final List<Long> excludedUserIds;

    public AnalyticsSupport(@Value("${moing.analytics.excluded-user-ids:}") String excludedUserIds) {
        this.excludedUserIds = parse(excludedUserIds);
    }

    private static List<Long> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of(SENTINEL_ID);
        }
        List<Long> ids = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
        return ids.isEmpty() ? List.of(SENTINEL_ID) : ids;
    }

    /** 어떤 사용자 id와도 같지 않은 값 (id는 IDENTITY라 1부터 증가한다) */
    private static final long SENTINEL_ID = -1L;

    /** 쿼리에 넘기는 제외 id 목록. 항상 1개 이상이다 */
    public List<Long> excludedUserIds() {
        return excludedUserIds;
    }
}
