package com.moing.backend.domain.admin.service;

import com.moing.backend.domain.admin.dto.AdminCoverageResponse;
import com.moing.backend.domain.admin.dto.AdminFreshnessResponse;
import com.moing.backend.domain.admin.dto.AdminParticipationResponse;
import com.moing.backend.domain.admin.dto.AdminPatternsResponse;
import com.moing.backend.domain.admin.dto.AdminTrustResponse;
import com.moing.backend.domain.admin.repository.AdminAnalyticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 서비스 지표 집계 테스트.
 *
 * <p>지표는 어워드 제출 근거로 쓰이므로 "실제보다 좋아 보이는 숫자"가 나오면 안 된다.
 * 그래서 검증의 중심은 집계가 도는지가 아니라 <b>무엇이 빠지는가</b>다 — 내부 계정,
 * 탈퇴 회원, 블라인드·친구 공개 리뷰가 각 지표에서 제외되는지를 본다.
 *
 * <p>리뷰·댓글은 JdbcTemplate으로 직접 넣는다. {@code created_at}이 @CreatedDate라
 * 엔티티로는 특정 시각을 심을 수 없는데, 72시간 경계와 KST 날짜 경계가 검증 대상이다.
 *
 * <p>현재 시각은 고정 {@link Clock}으로 못박는다. UTC로 2026-11-17 12:00이고 KST로는
 * 같은 날 21:00이라, {@code daily_visible}의 기준 시각(21시)이 오늘 분까지 잡힌다.
 */
@DataJpaTest
@Import({AdminAnalyticsService.class, AdminAnalyticsRepository.class, AnalyticsSupport.class,
        AdminAnalyticsServiceTest.FixedClockConfig.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:analytics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        // 내부 계정 제외가 설정에서 읽히는지까지 함께 검증한다
        "moing.analytics.excluded-user-ids=99, 98"
})
class AdminAnalyticsServiceTest {

    /** 고정 현재 시각 (UTC). KST로는 2026-11-17 21:00 */
    static final Instant NOW_INSTANT = Instant.parse("2026-11-17T12:00:00Z");

    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, ZoneOffset.UTC);

    private static final long USER_A = 1L;
    private static final long USER_B = 2L;
    private static final long INTERNAL = 99L;
    private static final long WITHDRAWN = 3L;

    private static final long PLACE_SEOUL = 10L;
    private static final long PLACE_GYEONGGI = 11L;
    private static final long PLACE_INCHEON = 12L;
    private static final long PLACE_BUSAN = 13L;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW_INSTANT, ZoneOffset.UTC);
        }
    }

    @Autowired
    private AdminAnalyticsService service;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM comment_moderations");
        jdbc.update("DELETE FROM comments");
        jdbc.update("DELETE FROM review_reports");
        jdbc.update("DELETE FROM reviews");
        jdbc.update("DELETE FROM places");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM seoul_areas");

        insertUser(USER_A, "유저A", null);
        insertUser(USER_B, "유저B", null);
        insertUser(INTERNAL, "팀원", null);
        insertUser(WITHDRAWN, "탈퇴자", NOW.minusDays(5));

        insertPlace(PLACE_SEOUL, "성수카페", "서울특별시 성동구 성수이로");
        insertPlace(PLACE_GYEONGGI, "판교카페", "경기도 성남시 분당구");
        insertPlace(PLACE_INCHEON, "송도카페", "인천광역시 연수구 송도동");
        insertPlace(PLACE_BUSAN, "해운대카페", "부산광역시 해운대구 우동");
    }

    // ---------------------------------------------------------------- participation

    @Test
    @DisplayName("참여 지표는 내부 계정과 탈퇴 회원을 빼고 센다")
    void 내부_계정과_탈퇴_회원_제외() {
        insertReview(1L, USER_A, PLACE_SEOUL, NOW.minusHours(1));
        insertReview(2L, USER_A, PLACE_SEOUL, NOW.minusHours(2));
        insertReview(3L, USER_B, PLACE_SEOUL, NOW.minusHours(3));
        // 아래 둘은 어떤 집계에도 들어가지 않아야 한다
        insertReview(4L, INTERNAL, PLACE_SEOUL, NOW.minusHours(1));
        insertReview(5L, WITHDRAWN, PLACE_SEOUL, NOW.minusHours(1));

        AdminParticipationResponse result = service.getParticipation();

        assertThat(result.totalUsers()).isEqualTo(2);
        assertThat(result.writers()).isEqualTo(2);
        assertThat(result.activationRate()).isEqualTo(100.0);
        assertThat(result.repeatWriters()).isEqualTo(1);
        assertThat(result.repeatRate()).isEqualTo(50.0);
        assertThat(result.totalReviews()).isEqualTo(3);
        assertThat(result.avgReviewsPerWriter()).isEqualTo(1.5);
    }

    @Test
    @DisplayName("주별 활동 사용자는 KST 월요일로 묶고, 리뷰와 댓글을 함께 센다")
    void weekly_active는_KST_월요일로_묶는다() {
        // 2026-11-16(월) 00:30 KST = 2026-11-15 15:30 UTC.
        // UTC로 끊으면 전주(11-09)로 잘못 묶이는 시각이다
        insertReview(1L, USER_A, PLACE_SEOUL, LocalDateTime.of(2026, 11, 15, 15, 30));
        // 같은 주에 댓글만 남긴 사용자도 활동으로 센다
        insertReview(2L, INTERNAL, PLACE_SEOUL, LocalDateTime.of(2026, 11, 16, 3, 0));
        insertComment(1L, USER_B, 1L, LocalDateTime.of(2026, 11, 16, 3, 0));
        // 전주(2026-11-09 시작)
        insertReview(3L, USER_A, PLACE_SEOUL, LocalDateTime.of(2026, 11, 10, 3, 0));

        List<AdminParticipationResponse.WeeklyActive> weekly = service.getParticipation().weeklyActive();

        assertThat(weekly).extracting(AdminParticipationResponse.WeeklyActive::weekStart)
                .containsExactly(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 16));
        assertThat(weekly.get(0).activeUsers()).isEqualTo(1);
        // USER_A(리뷰)와 USER_B(댓글). INTERNAL은 빠진다
        assertThat(weekly.get(1).activeUsers()).isEqualTo(2);
    }

    @Test
    @DisplayName("리뷰가 없으면 분모가 0이라 비율은 모두 0이다")
    void 비율_분모가_0() {
        AdminParticipationResponse result = service.getParticipation();

        assertThat(result.totalUsers()).isEqualTo(2);
        assertThat(result.writers()).isZero();
        assertThat(result.activationRate()).isZero();
        // writers가 0이라 repeat_rate의 분모도 0이다
        assertThat(result.repeatRate()).isZero();
        assertThat(result.avgReviewsPerWriter()).isZero();
    }

    // ---------------------------------------------------------------- freshness

    @Test
    @DisplayName("최신성은 블라인드·친구 공개 리뷰를 노출에서 뺀다")
    void 블라인드와_비공개_리뷰_제외() {
        insertReview(1L, USER_A, PLACE_SEOUL, NOW.minusHours(1));
        insertBlindedReview(2L, USER_A, PLACE_SEOUL, NOW.minusHours(1));
        insertFriendsOnlyReview(3L, USER_A, PLACE_SEOUL, NOW.minusHours(1));
        insertReview(4L, INTERNAL, PLACE_SEOUL, NOW.minusHours(1));

        AdminFreshnessResponse result = service.getFreshness();

        assertThat(result.visibleReviewsNow()).isEqualTo(1);
        assertThat(result.maxExposureHours()).isEqualTo(72);
    }

    @Test
    @DisplayName("노출 경계: 71시간 59분은 세고 72시간 1분은 세지 않는다")
    void 노출_72시간_경계() {
        insertReview(1L, USER_A, PLACE_SEOUL, NOW.minusHours(71).minusMinutes(59));
        insertReview(2L, USER_A, PLACE_SEOUL, NOW.minusHours(72).minusMinutes(1));

        AdminFreshnessResponse result = service.getFreshness();

        assertThat(result.visibleReviewsNow()).isEqualTo(1);
        // 71h59m = 71.98…h → 소수 첫째 자리로 72.0
        assertThat(result.medianAgeHoursNow()).isEqualTo(72.0);
    }

    @Test
    @DisplayName("노출 중인 리뷰가 0건이면 경과 시간은 0이 아니라 null이다")
    void 노출_0건이면_null() {
        insertReview(1L, USER_A, PLACE_SEOUL, NOW.minusHours(100));

        AdminFreshnessResponse result = service.getFreshness();

        assertThat(result.visibleReviewsNow()).isZero();
        assertThat(result.medianAgeHoursNow()).isNull();
        assertThat(result.avgAgeHoursNow()).isNull();
    }

    @Test
    @DisplayName("경과 시간 중앙값과 평균은 소수 첫째 자리로 내려간다")
    void 경과_시간_중앙값과_평균() {
        insertReview(1L, USER_A, PLACE_SEOUL, NOW.minusHours(1));
        insertReview(2L, USER_A, PLACE_SEOUL, NOW.minusHours(2));
        insertReview(3L, USER_A, PLACE_SEOUL, NOW.minusHours(10));

        AdminFreshnessResponse result = service.getFreshness();

        assertThat(result.visibleReviewsNow()).isEqualTo(3);
        assertThat(result.medianAgeHoursNow()).isEqualTo(2.0);
        // (1 + 2 + 10) / 3 = 4.333… → 4.3
        assertThat(result.avgAgeHoursNow()).isEqualTo(4.3);
    }

    @Test
    @DisplayName("daily_visible은 30일치를 각 날짜 21시(KST) 기준으로 세고, 0건인 날도 채운다")
    void daily_visible_기준_시각() {
        // 2026-11-16 20:00 KST (= 11:00 UTC) 작성.
        // 11-16 21시 기준으로는 1시간 전이라 노출, 11-17 21시 기준으로는 25시간 전이라 여전히 노출
        insertReview(1L, USER_A, PLACE_SEOUL, LocalDateTime.of(2026, 11, 16, 11, 0));
        // 11-13 21시 기준보다 더 오래된 리뷰 (73시간 전) — 어느 기준에도 안 걸린다
        insertReview(2L, USER_A, PLACE_SEOUL, LocalDateTime.of(2026, 11, 10, 9, 0));

        List<AdminFreshnessResponse.DailyVisible> daily = service.getFreshness().dailyVisible();

        assertThat(daily).hasSize(30);
        assertThat(daily.get(0).date()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(daily.get(29).date()).isEqualTo(LocalDate.of(2026, 11, 17));

        AdminFreshnessResponse.DailyVisible nov16 = dayOf(daily, LocalDate.of(2026, 11, 16));
        assertThat(nov16.visibleReviews()).isEqualTo(1);
        assertThat(nov16.medianAgeHours()).isEqualTo(1.0);

        AdminFreshnessResponse.DailyVisible nov17 = dayOf(daily, LocalDate.of(2026, 11, 17));
        assertThat(nov17.visibleReviews()).isEqualTo(1);
        assertThat(nov17.medianAgeHours()).isEqualTo(25.0);

        // 리뷰가 하나도 노출되지 않던 날은 0과 null로 자리를 채운다
        AdminFreshnessResponse.DailyVisible nov1 = dayOf(daily, LocalDate.of(2026, 11, 1));
        assertThat(nov1.visibleReviews()).isZero();
        assertThat(nov1.medianAgeHours()).isNull();
    }

    // ---------------------------------------------------------------- trust

    @Test
    @DisplayName("검열 지표는 SUSPECT만 의심으로 세고 사유별로 묶는다")
    void SUSPECT_집계와_사유_분류() {
        insertReview(1L, USER_A, PLACE_SEOUL, NOW.minusHours(1));
        insertComment(1L, USER_A, 1L, NOW.minusHours(1));
        insertComment(2L, USER_A, 1L, NOW.minusHours(1));
        insertComment(3L, USER_B, 1L, NOW.minusHours(1));
        insertComment(4L, USER_B, 1L, NOW.minusHours(1));
        insertComment(5L, INTERNAL, 1L, NOW.minusHours(1));

        insertModeration(1L, 1L, "SAFE", null);
        insertModeration(2L, 2L, "SUSPECT", "광고");
        insertModeration(3L, 3L, "SUSPECT", "광고");
        insertModeration(4L, 4L, "SUSPECT", "욕설");
        // 내부 계정 댓글의 검열 결과는 집계에서 빠진다
        insertModeration(5L, 5L, "SUSPECT", "스팸");

        AdminTrustResponse.Moderation moderation = service.getTrust().moderation();

        assertThat(moderation.checkedComments()).isEqualTo(4);
        assertThat(moderation.suspectComments()).isEqualTo(3);
        assertThat(moderation.suspectRate()).isEqualTo(75.0);
        assertThat(moderation.byCategory())
                .extracting(AdminTrustResponse.CategoryCount::category,
                        AdminTrustResponse.CategoryCount::count)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("광고", 2L),
                        org.assertj.core.groups.Tuple.tuple("욕설", 1L));
    }

    @Test
    @DisplayName("신고 지표는 블라인드 비율의 분모로 전체 리뷰 수를 함께 내려준다")
    void 신고와_블라인드_집계() {
        insertReview(1L, USER_A, PLACE_SEOUL, NOW.minusHours(1));
        insertReview(2L, USER_A, PLACE_SEOUL, NOW.minusHours(1));
        insertBlindedReview(3L, USER_B, PLACE_SEOUL, NOW.minusHours(1));
        insertReview(4L, USER_B, PLACE_SEOUL, NOW.minusHours(1));

        // 같은 리뷰에 신고가 둘 붙어도 신고된 리뷰는 하나로 센다
        insertReport(1L, 3L, USER_A);
        insertReport(2L, 3L, USER_B);
        // 내부 계정이 올린 신고는 세지 않는다
        insertReport(3L, 1L, INTERNAL);

        AdminTrustResponse.Reports reports = service.getTrust().reports();

        assertThat(reports.reportedReviews()).isEqualTo(1);
        assertThat(reports.blindedReviews()).isEqualTo(1);
        assertThat(reports.totalReviews()).isEqualTo(4);
        assertThat(reports.blindedRate()).isEqualTo(25.0);
    }

    // ---------------------------------------------------------------- coverage

    @Test
    @DisplayName("권역은 주소 첫 단어로 서울·수도권·비수도권으로 가른다")
    void region_group_분류() {
        insertReview(1L, USER_A, PLACE_SEOUL, NOW.minusHours(1));
        insertReview(2L, USER_A, PLACE_SEOUL, NOW.minusHours(2));
        insertReview(3L, USER_A, PLACE_GYEONGGI, NOW.minusHours(1));
        insertReview(4L, USER_B, PLACE_INCHEON, NOW.minusHours(1));
        insertReview(5L, USER_B, PLACE_BUSAN, NOW.minusHours(1));

        AdminCoverageResponse result = service.getCoverage();

        assertThat(result.regions())
                .extracting(AdminCoverageResponse.Region::regionGroup,
                        AdminCoverageResponse.Region::places,
                        AdminCoverageResponse.Region::reviews)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("서울", 1L, 2L),
                        // 경기 + 인천
                        org.assertj.core.groups.Tuple.tuple("수도권(경기·인천)", 2L, 2L),
                        org.assertj.core.groups.Tuple.tuple("비수도권", 1L, 1L));

        assertThat(result.userReportPlaces()).isEqualTo(4);
        assertThat(result.sigunguTop()).hasSize(4);
        assertThat(result.sigunguTop().get(0).sido()).isEqualTo("서울특별시");
        assertThat(result.sigunguTop().get(0).sigungu()).isEqualTo("성동구");
        assertThat(result.sigunguTop().get(0).reviews()).isEqualTo(2);
    }

    @Test
    @DisplayName("공공데이터 지점 수와 마지막 동기화 시각을 내려준다")
    void 공공데이터_지점() {
        insertSeoulArea(1L, "성수카페거리", NOW.minusHours(3));
        insertSeoulArea(2L, "강남역", NOW.minusHours(1));

        AdminCoverageResponse result = service.getCoverage();

        assertThat(result.publicDataPoints()).isEqualTo(2);
        assertThat(result.lastSyncedAt()).isEqualTo(NOW.minusHours(1));
    }

    // ---------------------------------------------------------------- patterns

    @Test
    @DisplayName("히트맵은 기록이 없는 칸까지 168칸을 모두 내려준다")
    void 히트맵_168칸() {
        // 2026-11-16(월) 23:00 KST = 14:00 UTC. KST로 변환하지 않으면 월요일 14시로 잘못 잡힌다
        insertReview(1L, USER_A, PLACE_SEOUL, LocalDateTime.of(2026, 11, 16, 14, 0));
        // 일요일(7) 01시 KST = 토요일 16:00 UTC
        insertReview(2L, USER_A, PLACE_SEOUL, LocalDateTime.of(2026, 11, 14, 16, 0));

        List<AdminPatternsResponse.HeatmapCell> heatmap = service.getPatterns().reviewHeatmap();

        assertThat(heatmap).hasSize(168);
        assertThat(heatmap.get(0).dayOfWeek()).isEqualTo(1);
        assertThat(heatmap.get(0).hour()).isZero();
        assertThat(heatmap.get(167).dayOfWeek()).isEqualTo(7);
        assertThat(heatmap.get(167).hour()).isEqualTo(23);

        assertThat(cell(heatmap, 1, 23).reviews()).isEqualTo(1);
        assertThat(cell(heatmap, 7, 1).reviews()).isEqualTo(1);
        // 기록이 없는 칸은 0으로 채운다
        assertThat(cell(heatmap, 3, 12).reviews()).isZero();
        assertThat(heatmap.stream().mapToLong(AdminPatternsResponse.HeatmapCell::reviews).sum())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("혼잡도 분포는 비율의 분모를 함께 내려준다")
    void 혼잡도_분포() {
        insertReviewWithCongestion(1L, USER_A, PLACE_SEOUL, NOW.minusHours(1), "CROWDED");
        insertReviewWithCongestion(2L, USER_A, PLACE_SEOUL, NOW.minusHours(2), "CROWDED");
        insertReviewWithCongestion(3L, USER_B, PLACE_SEOUL, NOW.minusHours(3), "RELAXED");
        // 혼잡도를 적지 않은 리뷰는 분모에서 빠진다
        insertReview(4L, USER_B, PLACE_SEOUL, NOW.minusHours(4));

        List<AdminPatternsResponse.Congestion> congestion = service.getPatterns().congestion();

        assertThat(congestion)
                .extracting(AdminPatternsResponse.Congestion::congestionLevel,
                        AdminPatternsResponse.Congestion::reviews,
                        AdminPatternsResponse.Congestion::rate)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("CROWDED", 2L, 66.7),
                        org.assertj.core.groups.Tuple.tuple("RELAXED", 1L, 33.3));
        assertThat(congestion.get(0).reviewsWithCongestion()).isEqualTo(3);
    }

    // ---------------------------------------------------------------- 도우미

    private static AdminFreshnessResponse.DailyVisible dayOf(
            List<AdminFreshnessResponse.DailyVisible> daily, LocalDate date) {
        return daily.stream().filter(d -> d.date().equals(date)).findFirst().orElseThrow();
    }

    private static AdminPatternsResponse.HeatmapCell cell(
            List<AdminPatternsResponse.HeatmapCell> heatmap, int dayOfWeek, int hour) {
        return heatmap.stream()
                .filter(c -> c.dayOfWeek() == dayOfWeek && c.hour() == hour)
                .findFirst().orElseThrow();
    }

    private void insertUser(long id, String nickname, LocalDateTime deletedAt) {
        jdbc.update("""
                INSERT INTO users (id, social_provider, social_id, nickname, terms_agreed,
                                   location_terms_agreed, privacy_agreed, marketing_agreed,
                                   created_at, updated_at, deleted_at)
                VALUES (?, 'KAKAO', ?, ?, true, true, true, false, ?, ?, ?)
                """, id, "social-" + id, nickname, NOW.minusDays(30), NOW.minusDays(30), deletedAt);
    }

    private void insertPlace(long id, String name, String address) {
        jdbc.update("""
                INSERT INTO places (id, name, address, category, is_active, created_at)
                VALUES (?, ?, ?, 'CAFE', true, ?)
                """, id, name, address, NOW.minusDays(20));
    }

    private void insertReview(long id, long userId, long placeId, LocalDateTime createdAt) {
        insertReviewRow(id, userId, placeId, createdAt, false, null, null);
    }

    private void insertBlindedReview(long id, long userId, long placeId, LocalDateTime createdAt) {
        insertReviewRow(id, userId, placeId, createdAt, true, null, null);
    }

    private void insertFriendsOnlyReview(long id, long userId, long placeId, LocalDateTime createdAt) {
        insertReviewRow(id, userId, placeId, createdAt, false, "FRIENDS", null);
    }

    private void insertReviewWithCongestion(long id, long userId, long placeId,
                                            LocalDateTime createdAt, String congestionLevel) {
        insertReviewRow(id, userId, placeId, createdAt, false, null, congestionLevel);
    }

    private void insertReviewRow(long id, long userId, long placeId, LocalDateTime createdAt,
                                 boolean blinded, String visibility, String congestionLevel) {
        jdbc.update("""
                INSERT INTO reviews (id, user_id, place_id, congestion_level, visibility, status,
                                     is_blinded, helpful_count, view_count, created_at)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, 0, 0, ?)
                """, id, userId, placeId, congestionLevel,
                visibility == null ? "PUBLIC" : visibility, blinded, createdAt);
    }

    private void insertComment(long id, long userId, long reviewId, LocalDateTime createdAt) {
        jdbc.update("""
                INSERT INTO comments (id, review_id, user_id, content, is_secret, is_deleted, created_at)
                VALUES (?, ?, ?, '댓글', false, false, ?)
                """, id, reviewId, userId, createdAt);
    }

    private void insertModeration(long id, long commentId, String verdict, String category) {
        jdbc.update("""
                INSERT INTO comment_moderations (id, comment_id, verdict, category, model, checked_at)
                VALUES (?, ?, ?, ?, 'gemini-test', ?)
                """, id, commentId, verdict, category, NOW.minusHours(1));
    }

    private void insertReport(long id, long reviewId, long reporterId) {
        jdbc.update("""
                INSERT INTO review_reports (id, review_id, reporter_id, reason, status, created_at)
                VALUES (?, ?, ?, 'SPAM', 'PENDING', ?)
                """, id, reviewId, reporterId, NOW.minusHours(1));
    }

    private void insertSeoulArea(long id, String areaNm, LocalDateTime syncedAt) {
        jdbc.update("""
                INSERT INTO seoul_areas (id, area_nm, category, latitude, longitude, synced_at)
                VALUES (?, ?, '발달상권', 37.5, 127.0, ?)
                """, id, areaNm, syncedAt);
    }
}
