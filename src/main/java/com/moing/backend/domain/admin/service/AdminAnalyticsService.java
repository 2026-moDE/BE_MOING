package com.moing.backend.domain.admin.service;

import com.moing.backend.domain.admin.dto.AdminCoverageResponse;
import com.moing.backend.domain.admin.dto.AdminFreshnessResponse;
import com.moing.backend.domain.admin.dto.AdminParticipationResponse;
import com.moing.backend.domain.admin.dto.AdminPatternsResponse;
import com.moing.backend.domain.admin.dto.AdminTrustResponse;
import com.moing.backend.domain.admin.repository.AdminAnalyticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 서비스 지표 조회.
 *
 * <p>집계는 모두 {@link AdminAnalyticsRepository}가 하고, 여기서는 비율 계산과
 * 빈 칸 채우기처럼 응답 형태를 맞추는 일만 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAnalyticsService {

    /** 수도권으로 묶는 시·도 (주소 첫 단어) */
    private static final String REGION_SEOUL = "서울";
    private static final String REGION_METRO = "수도권(경기·인천)";
    private static final String REGION_OUTSIDE = "비수도권";

    private static final List<String> REGION_ORDER = List.of(REGION_SEOUL, REGION_METRO, REGION_OUTSIDE);

    /** 리뷰 수 상위 몇 개 시·군·구를 내려줄지 */
    private static final int SIGUNGU_TOP_LIMIT = 10;

    private static final int DAYS_IN_WEEK = 7;
    private static final int HOURS_IN_DAY = 24;

    private final AdminAnalyticsRepository analyticsRepository;
    private final Clock clock;

    // ---------------------------------------------------------------- participation

    public AdminParticipationResponse getParticipation() {
        long totalUsers = analyticsRepository.countRealUsers();
        AdminAnalyticsRepository.WriterStats writers = analyticsRepository.findWriterStats();

        List<AdminParticipationResponse.WeeklyActive> weekly = analyticsRepository.findWeeklyActive().stream()
                .map(row -> new AdminParticipationResponse.WeeklyActive(row.weekStart(), row.activeUsers()))
                .toList();

        return new AdminParticipationResponse(
                totalUsers,
                writers.writers(),
                percentage(writers.writers(), totalUsers),
                writers.repeatWriters(),
                percentage(writers.repeatWriters(), writers.writers()),
                ratio(writers.totalReviews(), writers.writers()),
                writers.totalReviews(),
                weekly);
    }

    // ---------------------------------------------------------------- freshness

    public AdminFreshnessResponse getFreshness() {
        LocalDateTime now = nowUtc();
        AdminAnalyticsRepository.VisibleNow visible = analyticsRepository.findVisibleNow(now);

        List<LocalDateTime> snapshots = dailySnapshots(now);
        Map<LocalDateTime, AdminAnalyticsRepository.DailyVisibleRow> rows = new LinkedHashMap<>();
        analyticsRepository.findDailyVisible(snapshots)
                .forEach(row -> rows.put(row.snapshotAt(), row));

        // 조회 결과에 없는 기준 시각은 노출이 0건이던 날이다. 응답에서 날짜가 빠지면
        // 그래프에 구멍이 생기므로 0으로 채운다
        List<AdminFreshnessResponse.DailyVisible> daily = new ArrayList<>();
        for (LocalDateTime snapshot : snapshots) {
            AdminAnalyticsRepository.DailyVisibleRow row = rows.get(snapshot);
            LocalDate date = toKstDate(snapshot);
            if (row == null) {
                daily.add(new AdminFreshnessResponse.DailyVisible(date, 0, null));
            } else {
                daily.add(new AdminFreshnessResponse.DailyVisible(
                        date, row.visibleReviews(), round1(row.medianAgeHours())));
            }
        }

        return new AdminFreshnessResponse(
                visible.visibleReviews(),
                round1(visible.medianAgeHours()),
                round1(visible.avgAgeHours()),
                daily,
                AnalyticsSupport.MAX_EXPOSURE_HOURS);
    }

    /**
     * {@code daily_visible}의 기준 시각들을 오래된 날부터 만든다.
     *
     * <p>하루를 대표하는 시각은 KST {@value AnalyticsSupport#DAILY_SNAPSHOT_HOUR_KST}시인데,
     * 오늘 그 시각이 아직 지나지 않았으면 미래를 기준으로 셀 수 없으므로 어제부터 센다.
     *
     * @param now 현재 시각(UTC)
     * @return UTC 기준 시각 목록 (오름차순)
     */
    private List<LocalDateTime> dailySnapshots(LocalDateTime now) {
        ZonedDateTime nowKst = now.atOffset(ZoneOffset.UTC).atZoneSameInstant(AnalyticsSupport.KST);
        ZonedDateTime latest = nowKst
                .withHour(AnalyticsSupport.DAILY_SNAPSHOT_HOUR_KST)
                .withMinute(0).withSecond(0).withNano(0);
        if (latest.isAfter(nowKst)) {
            latest = latest.minusDays(1);
        }

        List<LocalDateTime> snapshots = new ArrayList<>();
        for (int i = AnalyticsSupport.DAILY_VISIBLE_DAYS - 1; i >= 0; i--) {
            snapshots.add(toUtc(latest.minusDays(i)));
        }
        return snapshots;
    }

    // ---------------------------------------------------------------- trust

    public AdminTrustResponse getTrust() {
        AdminAnalyticsRepository.ModerationStats moderation = analyticsRepository.findModerationStats();
        List<AdminTrustResponse.CategoryCount> categories = analyticsRepository.findSuspectCategories().stream()
                .map(row -> new AdminTrustResponse.CategoryCount(row.category(), row.count()))
                .toList();

        AdminAnalyticsRepository.ReportStats reports = analyticsRepository.findReportStats();
        long totalReviews = analyticsRepository.countRealUserReviews();

        return new AdminTrustResponse(
                new AdminTrustResponse.Moderation(
                        moderation.checkedComments(),
                        moderation.suspectComments(),
                        percentage(moderation.suspectComments(), moderation.checkedComments()),
                        categories),
                new AdminTrustResponse.Reports(
                        reports.reportedReviews(),
                        reports.blindedReviews(),
                        totalReviews,
                        percentage(reports.blindedReviews(), totalReviews)));
    }

    // ---------------------------------------------------------------- coverage

    public AdminCoverageResponse getCoverage() {
        AdminAnalyticsRepository.SeoulAreaStats seoulAreas = analyticsRepository.findSeoulAreaStats();
        List<AdminAnalyticsRepository.SigunguRow> sigungu = analyticsRepository.findSigunguStats();

        List<AdminCoverageResponse.Sigungu> top = sigungu.stream()
                .limit(SIGUNGU_TOP_LIMIT)
                .map(row -> new AdminCoverageResponse.Sigungu(
                        row.sido(), row.sigungu(), row.places(), row.reviews()))
                .toList();

        List<AdminCoverageResponse.Category> categories = analyticsRepository.findCategoryStats().stream()
                .map(row -> new AdminCoverageResponse.Category(row.category(), row.places(), row.reviews()))
                .toList();

        return new AdminCoverageResponse(
                analyticsRepository.countPlacesWithUserReviews(),
                seoulAreas.dataPoints(),
                seoulAreas.lastSyncedAt(),
                groupByRegion(sigungu),
                top,
                categories);
    }

    /**
     * 시·군·구 집계를 세 권역으로 묶는다.
     *
     * <p>장소 하나는 주소가 하나라 두 권역에 걸치지 않으므로 그대로 더해도 중복되지 않는다.
     * 기록이 없는 권역도 0으로 내려줘 "어디가 비어 있는지"가 보이게 한다.
     */
    private List<AdminCoverageResponse.Region> groupByRegion(List<AdminAnalyticsRepository.SigunguRow> rows) {
        Map<String, long[]> totals = new LinkedHashMap<>();
        REGION_ORDER.forEach(group -> totals.put(group, new long[2]));

        for (AdminAnalyticsRepository.SigunguRow row : rows) {
            long[] sum = totals.get(regionGroupOf(row.sido()));
            sum[0] += row.places();
            sum[1] += row.reviews();
        }

        return REGION_ORDER.stream()
                .map(group -> new AdminCoverageResponse.Region(
                        group, totals.get(group)[0], totals.get(group)[1]))
                .toList();
    }

    /** 주소 첫 단어로 권역을 가른다. "서울시"처럼 줄여 쓴 주소도 받도록 접두사로 본다 */
    private String regionGroupOf(String sido) {
        if (sido == null) {
            return REGION_OUTSIDE;
        }
        if (sido.startsWith("서울")) {
            return REGION_SEOUL;
        }
        if (sido.startsWith("경기") || sido.startsWith("인천")) {
            return REGION_METRO;
        }
        return REGION_OUTSIDE;
    }

    // ---------------------------------------------------------------- patterns

    public AdminPatternsResponse getPatterns() {
        Map<Integer, Long> counts = new LinkedHashMap<>();
        analyticsRepository.findReviewHeatmap()
                .forEach(row -> counts.put(cellKey(row.dayOfWeek(), row.hour()), row.reviews()));

        // 기록이 없는 칸은 행이 없다. 히트맵은 빈 칸도 자리를 차지해야 하므로 168칸을 모두 만든다
        List<AdminPatternsResponse.HeatmapCell> heatmap = new ArrayList<>(DAYS_IN_WEEK * HOURS_IN_DAY);
        for (int day = 1; day <= DAYS_IN_WEEK; day++) {
            for (int hour = 0; hour < HOURS_IN_DAY; hour++) {
                heatmap.add(new AdminPatternsResponse.HeatmapCell(
                        day, hour, counts.getOrDefault(cellKey(day, hour), 0L)));
            }
        }

        List<AdminAnalyticsRepository.CongestionRow> rows = analyticsRepository.findCongestionStats();
        long withCongestion = rows.stream().mapToLong(AdminAnalyticsRepository.CongestionRow::reviews).sum();
        List<AdminPatternsResponse.Congestion> congestion = rows.stream()
                .map(row -> new AdminPatternsResponse.Congestion(
                        row.congestionLevel(),
                        row.reviews(),
                        withCongestion,
                        percentage(row.reviews(), withCongestion)))
                .toList();

        return new AdminPatternsResponse(heatmap, congestion);
    }

    private static int cellKey(int dayOfWeek, int hour) {
        return dayOfWeek * HOURS_IN_DAY + hour;
    }

    // ---------------------------------------------------------------- 공통 계산

    /** 현재 시각(UTC). created_at이 UTC로 저장되므로 비교 기준도 UTC다 */
    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static LocalDateTime toUtc(ZonedDateTime kst) {
        return kst.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static LocalDate toKstDate(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(AnalyticsSupport.KST).toLocalDate();
    }

    /** 백분율. 분모가 0이면 0을 준다 (표본이 없는 것을 100%로 읽히게 하면 안 된다) */
    private static double percentage(long numerator, long denominator) {
        if (denominator == 0) {
            return 0;
        }
        return round1(numerator * 100.0 / denominator);
    }

    /** 1건당 평균. 분모가 0이면 0을 준다 */
    private static double ratio(long numerator, long denominator) {
        if (denominator == 0) {
            return 0;
        }
        return round1(numerator * 1.0 / denominator);
    }

    private static double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /** 집계 대상이 0건이면 null이 그대로 내려가야 한다 (0.0은 "0시간 전"으로 읽힌다) */
    private static Double round1(Double value) {
        return value == null ? null : round1(value.doubleValue());
    }
}
