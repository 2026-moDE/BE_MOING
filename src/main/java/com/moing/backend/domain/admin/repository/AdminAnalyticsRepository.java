package com.moing.backend.domain.admin.repository;

import com.moing.backend.domain.admin.service.AnalyticsSupport;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 서비스 지표 집계 쿼리.
 *
 * <p>지표는 "실사용자가 제약 속에서도 기록을 남기는가"를 보여주는 것이 목적이라 거의 모든
 * 쿼리가 집계이고, JPA로 표현하면 조회 메서드가 흩어진다. 그래서 네이티브 SQL을 여기 모은다.
 *
 * <h2>시간대</h2>
 * {@code created_at}은 UTC로 저장된다({@code BackendApplication}이 JVM 기본 시간대를 UTC로
 * 고정하고 {@code @CreatedDate}가 그 시각을 넣는다). 날짜·요일·시각으로 끊는 집계는 KST 기준
 * 이어야 하므로 SQL에서 {@code :kstOffsetHours}만큼 옮겨 계산한다.
 *
 * <h2>H2와 PostgreSQL을 함께 통과하는 문법</h2>
 * 운영은 PostgreSQL, 테스트는 H2(PostgreSQL 모드)다. 둘의 동작이 갈리는 문법이 있어
 * 양쪽에서 같은 값을 내는 것만 쓴다.
 * <ul>
 *   <li>{@code date_trunc('week', ...)}는 쓰지 않는다 — H2는 일요일, PostgreSQL은 월요일을
 *       주 시작으로 삼아 결과가 하루 어긋난다. 월요일은 {@code isodow}로 직접 뺀다.</li>
 *   <li>{@code split_part}, {@code AT TIME ZONE} 연쇄, {@code interval '9 hours'}는 H2에 없다.
 *       각각 {@code position}+{@code substring}, 오프셋 덧셈, {@code interval '1' hour} 곱으로 쓴다.</li>
 * </ul>
 *
 * <h2>조건절을 상수로 두고 {@code %s}로 끼우는 이유</h2>
 * "실사용자"와 "노출 중인 리뷰"의 정의는 여러 쿼리가 공유하는데, 복제해 두면 한 곳만
 * 고쳐도 지표끼리 분모가 어긋난다. 텍스트 블록은 줄 끝 공백을 지워 이어붙이면
 * {@code WHEREEXISTS}처럼 붙어버리므로 {@code formatted}로 끼운다.
 */
@Repository
public class AdminAnalyticsRepository {

    /**
     * 집계 대상 사용자 조건 (사용자 별칭 {@code u} 기준).
     *
     * <p>{@code users}에는 {@code @SQLRestriction}으로 탈퇴자를 걸러내는 설정이 있지만
     * 네이티브 SQL에는 적용되지 않으므로 직접 쓴다.
     */
    private static final String REAL_USER =
            "u.deleted_at IS NULL AND u.id NOT IN (:excludedIds)";

    /** 리뷰 작성자가 실사용자인지 (리뷰 별칭 {@code r} 기준) */
    private static final String REVIEW_BY_REAL_USER =
            "EXISTS (SELECT 1 FROM users u WHERE u.id = r.user_id AND " + REAL_USER + ")";

    /** 댓글 작성자가 실사용자인지 (댓글 별칭 {@code c} 기준) */
    private static final String COMMENT_BY_REAL_USER =
            "EXISTS (SELECT 1 FROM users u WHERE u.id = c.user_id AND " + REAL_USER + ")";

    /** 지도에 노출되는 리뷰 조건. {@code PlaceRepository.findHotPlaces}의 노출 기준과 같다 */
    private static final String VISIBLE_REVIEW =
            "r.is_blinded = false AND (r.visibility IS NULL OR r.visibility <> 'FRIENDS')";

    /**
     * UTC로 저장된 시각을 KST로 옮긴 식.
     *
     * <p>오프셋과 노출 시간은 파라미터가 아니라 SQL에 적어 넣는다 — H2가 산술식에 들어온
     * 바인딩 값의 타입을 추론하지 못해 {@code UNKNOWN - TIMESTAMP}로 막힌다. 둘 다 코드가
     * 정한 정수 상수라 값이 외부에서 들어올 일이 없다.
     */
    private static final String KST_AT =
            "(%s + interval '" + AnalyticsSupport.kstOffsetHours() + "' hour)";

    /** 노출 기간만큼 거슬러 올라간 시각을 만드는 식 */
    private static final String EXPOSURE_WINDOW =
            "interval '" + AnalyticsSupport.MAX_EXPOSURE_HOURS + "' hour";

    private final NamedParameterJdbcTemplate jdbc;
    private final AnalyticsSupport support;

    public AdminAnalyticsRepository(NamedParameterJdbcTemplate jdbc, AnalyticsSupport support) {
        this.jdbc = jdbc;
        this.support = support;
    }

    /** 모든 쿼리가 공유하는 파라미터(제외 계정)를 담은 바인딩을 만든다 */
    private MapSqlParameterSource params() {
        return new MapSqlParameterSource()
                .addValue("excludedIds", support.excludedUserIds());
    }

    // ---------------------------------------------------------------- participation

    /** 탈퇴하지 않은 실사용자 수 */
    public long countRealUsers() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM users u WHERE %s
                """.formatted(REAL_USER), params(), Long.class);
    }

    /** 실사용자가 쓴 리뷰 총수. 비율의 분모로 쓴다 */
    public long countRealUserReviews() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM reviews r WHERE %s
                """.formatted(REVIEW_BY_REAL_USER), params(), Long.class);
    }

    /**
     * 리뷰를 쓴 사람 수, 2회 이상 쓴 사람 수, 리뷰 총수를 한 번에 센다.
     * 세 값의 분모·분자가 서로 맞아야 해서 각각 따로 세지 않는다.
     */
    public WriterStats findWriterStats() {
        return jdbc.queryForObject("""
                SELECT count(*) AS writers,
                       count(*) FILTER (WHERE review_count >= 2) AS repeat_writers,
                       coalesce(sum(review_count), 0) AS total_reviews
                FROM (
                  SELECT r.user_id, count(*) AS review_count
                  FROM reviews r
                  WHERE %s
                  GROUP BY r.user_id
                ) w
                """.formatted(REVIEW_BY_REAL_USER), params(), (rs, n) -> new WriterStats(
                rs.getLong("writers"),
                rs.getLong("repeat_writers"),
                rs.getLong("total_reviews")));
    }

    /**
     * 주별 활동 사용자 수. 활동은 리뷰나 댓글을 하나라도 남긴 것으로 센다.
     *
     * <p>주 시작은 KST 월요일이다. 삭제된 댓글도 활동으로 센다 — 그 주에 기록을 남겼다는
     * 사실은 지워지지 않는다.
     */
    public List<WeeklyActiveRow> findWeeklyActive() {
        String weekStart = "cast(%1$s AS date) - cast(extract(isodow FROM %1$s) AS int) + 1"
                .formatted(KST_AT.formatted("created_at"));

        return jdbc.query("""
                SELECT week_start, count(DISTINCT user_id) AS active_users
                FROM (
                  SELECT r.user_id AS user_id, %1$s AS week_start
                  FROM reviews r
                  WHERE %2$s
                  UNION ALL
                  SELECT c.user_id AS user_id, %1$s AS week_start
                  FROM comments c
                  WHERE %3$s
                ) activity
                GROUP BY week_start
                ORDER BY week_start
                """.formatted(weekStart, REVIEW_BY_REAL_USER, COMMENT_BY_REAL_USER),
                params(), (rs, n) -> new WeeklyActiveRow(
                        rs.getObject("week_start", LocalDate.class),
                        rs.getLong("active_users")));
    }

    // ---------------------------------------------------------------- freshness

    /**
     * 기준 시각에 지도에 노출 중인 리뷰 수와 작성 후 경과 시간.
     *
     * @param now 기준 시각(UTC)
     */
    public VisibleNow findVisibleNow(LocalDateTime now) {
        MapSqlParameterSource p = params().addValue("now", now);

        return jdbc.queryForObject("""
                SELECT count(*) AS visible_reviews,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY age_hours) AS median_age_hours,
                       avg(age_hours) AS avg_age_hours
                FROM (
                  SELECT extract(epoch FROM (cast(:now AS timestamp) - r.created_at)) / 3600.0
                           AS age_hours
                  FROM reviews r
                  WHERE r.created_at > cast(:now AS timestamp) - %s
                    AND r.created_at <= cast(:now AS timestamp)
                    AND %s
                    AND %s
                ) visible
                """.formatted(EXPOSURE_WINDOW, VISIBLE_REVIEW, REVIEW_BY_REAL_USER),
                p, (rs, n) -> new VisibleNow(
                        rs.getLong("visible_reviews"),
                        nullableDouble(rs, "median_age_hours"),
                        nullableDouble(rs, "avg_age_hours")));
    }

    /**
     * 주어진 기준 시각들에 각각 노출 중이던 리뷰 수와 경과 시간 중앙값.
     *
     * <p>기준 시각은 하루에 하나씩 넘어오는데, 엔진에 따라 수열 생성 함수가 달라
     * ({@code generate_series} / {@code system_range}) SQL에서 만들지 않고 받은 목록을
     * {@code VALUES}로 세운 뒤 리뷰를 좌측 조인한다. 노출이 0건인 날은 조인이 비어
     * {@code visible_reviews}가 0, 중앙값이 null이 된다.
     *
     * @param snapshots 기준 시각 목록(UTC)
     */
    public List<DailyVisibleRow> findDailyVisible(List<LocalDateTime> snapshots) {
        if (snapshots.isEmpty()) {
            return List.of();
        }

        String values = IntStream.range(0, snapshots.size())
                .mapToObj(i -> "(cast(:snap" + i + " AS timestamp))")
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();

        MapSqlParameterSource p = params();
        for (int i = 0; i < snapshots.size(); i++) {
            p.addValue("snap" + i, snapshots.get(i));
        }

        return jdbc.query("""
                SELECT s.snap_at,
                       count(r.id) AS visible_reviews,
                       percentile_cont(0.5) WITHIN GROUP (
                         ORDER BY extract(epoch FROM (s.snap_at - r.created_at)) / 3600.0
                       ) AS median_age_hours
                FROM (VALUES %s) AS s(snap_at)
                LEFT JOIN reviews r
                  ON r.created_at > s.snap_at - %s
                 AND r.created_at <= s.snap_at
                 AND %s
                 AND %s
                GROUP BY s.snap_at
                ORDER BY s.snap_at
                """.formatted(values, EXPOSURE_WINDOW, VISIBLE_REVIEW, REVIEW_BY_REAL_USER),
                p, (rs, n) -> new DailyVisibleRow(
                        rs.getObject("snap_at", LocalDateTime.class),
                        rs.getLong("visible_reviews"),
                        nullableDouble(rs, "median_age_hours")));
    }

    // ---------------------------------------------------------------- trust

    /** AI 검열을 거친 댓글 수와 그중 의심 판정 수 */
    public ModerationStats findModerationStats() {
        return jdbc.queryForObject("""
                SELECT count(*) AS checked_comments,
                       count(*) FILTER (WHERE m.verdict = 'SUSPECT') AS suspect_comments
                FROM comment_moderations m
                JOIN comments c ON c.id = m.comment_id
                WHERE %s
                """.formatted(COMMENT_BY_REAL_USER), params(), (rs, n) -> new ModerationStats(
                rs.getLong("checked_comments"),
                rs.getLong("suspect_comments")));
    }

    /** 의심 판정의 사유 분류별 건수 (많은 순) */
    public List<CategoryCount> findSuspectCategories() {
        return jdbc.query("""
                SELECT m.category, count(*) AS count
                FROM comment_moderations m
                JOIN comments c ON c.id = m.comment_id
                WHERE m.verdict = 'SUSPECT'
                  AND %s
                GROUP BY m.category
                ORDER BY count(*) DESC, m.category
                """.formatted(COMMENT_BY_REAL_USER), params(), (rs, n) -> new CategoryCount(
                rs.getString("category"),
                rs.getLong("count")));
    }

    /**
     * 신고된 리뷰 수와 블라인드된 리뷰 수.
     *
     * <p>신고는 실사용자가 올린 것만 센다(내부 계정의 테스트 신고 제외). 신고 대상 리뷰도
     * 실사용자 리뷰로 한정해 {@code blinded_rate}의 분모와 기준이 어긋나지 않게 한다.
     */
    public ReportStats findReportStats() {
        return jdbc.queryForObject("""
                SELECT (
                  SELECT count(DISTINCT rp.review_id)
                  FROM review_reports rp
                  JOIN reviews r ON r.id = rp.review_id
                  WHERE %1$s
                    AND EXISTS (SELECT 1 FROM users u WHERE u.id = rp.reporter_id AND %2$s)
                ) AS reported_reviews,
                (
                  SELECT count(*)
                  FROM reviews r
                  WHERE r.is_blinded = true AND %1$s
                ) AS blinded_reviews
                """.formatted(REVIEW_BY_REAL_USER, REAL_USER), params(), (rs, n) -> new ReportStats(
                rs.getLong("reported_reviews"),
                rs.getLong("blinded_reviews")));
    }

    // ---------------------------------------------------------------- coverage

    /** 실사용자 리뷰가 1건 이상 있는 장소 수 */
    public long countPlacesWithUserReviews() {
        return jdbc.queryForObject("""
                SELECT count(DISTINCT r.place_id) FROM reviews r WHERE %s
                """.formatted(REVIEW_BY_REAL_USER), params(), Long.class);
    }

    /** 서울시 실시간 도시데이터로 혼잡도를 제공하는 지점 수와 마지막 동기화 시각 */
    public SeoulAreaStats findSeoulAreaStats() {
        return jdbc.queryForObject("""
                SELECT count(*) AS data_points, max(synced_at) AS last_synced_at
                FROM seoul_areas
                """, params(), (rs, n) -> new SeoulAreaStats(
                rs.getLong("data_points"),
                rs.getObject("last_synced_at", LocalDateTime.class)));
    }

    /**
     * 시·도와 시·군·구별 장소 수와 리뷰 수.
     *
     * <p>주소에서 첫 단어를 시·도, 두 번째 단어를 시·군·구로 본다. {@code split_part}가 H2에
     * 없어 {@code position}으로 공백 위치를 찾아 잘라내는데, 한 단계에서 앞서 만든 별칭을
     * 다시 참조할 수 없어 중첩 서브쿼리로 두 번에 걸쳐 자른다.
     *
     * <p>장소는 리뷰가 1건 이상인 것만 센다 — 비어 있는 장소까지 넣으면 "정보 공백을 메웠다"는
     * 지표가 실제 기록량보다 넓어 보인다. {@code user_report_places}와 같은 기준이다.
     */
    public List<SigunguRow> findSigunguStats() {
        return jdbc.query("""
                SELECT sido,
                       CASE WHEN tail_space > 0 THEN substring(tail, 1, tail_space - 1)
                            ELSE tail END AS sigungu,
                       count(DISTINCT place_id) AS places,
                       count(review_id) AS reviews
                FROM (
                  SELECT cut.place_id, cut.review_id, cut.sido, cut.tail,
                         position(' ' IN cut.tail) AS tail_space
                  FROM (
                    SELECT p.id AS place_id, r.id AS review_id,
                           CASE WHEN position(' ' IN p.address) > 0
                                THEN substring(p.address, 1, position(' ' IN p.address) - 1)
                                ELSE p.address END AS sido,
                           CASE WHEN position(' ' IN p.address) > 0
                                THEN substring(p.address, position(' ' IN p.address) + 1)
                                ELSE '' END AS tail
                    FROM places p
                    JOIN reviews r ON r.place_id = p.id
                    WHERE %s
                  ) cut
                ) split
                GROUP BY sido, sigungu
                ORDER BY count(review_id) DESC, sido, sigungu
                """.formatted(REVIEW_BY_REAL_USER), params(), (rs, n) -> new SigunguRow(
                rs.getString("sido"),
                rs.getString("sigungu"),
                rs.getLong("places"),
                rs.getLong("reviews")));
    }

    /** 장소 분류별 장소 수와 리뷰 수 */
    public List<PlaceCategoryRow> findCategoryStats() {
        return jdbc.query("""
                SELECT p.category,
                       count(DISTINCT p.id) AS places,
                       count(r.id) AS reviews
                FROM places p
                JOIN reviews r ON r.place_id = p.id
                WHERE %s
                GROUP BY p.category
                ORDER BY count(r.id) DESC, p.category
                """.formatted(REVIEW_BY_REAL_USER), params(), (rs, n) -> new PlaceCategoryRow(
                rs.getString("category"),
                rs.getLong("places"),
                rs.getLong("reviews")));
    }

    // ---------------------------------------------------------------- patterns

    /**
     * KST 요일(1=월~7=일)·시각별 리뷰 수. 기록이 없는 칸은 행이 없다.
     *
     * <p>시각 컬럼의 별칭이 {@code hour_of_day}인 것은 {@code hour}가 H2 예약어라
     * 별칭으로 쓸 수 없기 때문이다(PostgreSQL은 받아준다).
     */
    public List<HeatmapRow> findReviewHeatmap() {
        return jdbc.query("""
                SELECT extract(isodow FROM kst_at) AS day_of_week,
                       extract(hour FROM kst_at) AS hour_of_day,
                       count(*) AS reviews
                FROM (
                  SELECT %s AS kst_at
                  FROM reviews r
                  WHERE %s
                ) shifted
                GROUP BY extract(isodow FROM kst_at), extract(hour FROM kst_at)
                """.formatted(KST_AT.formatted("r.created_at"), REVIEW_BY_REAL_USER),
                params(), (rs, n) -> new HeatmapRow(
                        rs.getInt("day_of_week"),
                        rs.getInt("hour_of_day"),
                        rs.getLong("reviews")));
    }

    /** 혼잡도 단계별 리뷰 수. 혼잡도를 적지 않은 리뷰는 세지 않는다 */
    public List<CongestionRow> findCongestionStats() {
        return jdbc.query("""
                SELECT r.congestion_level, count(*) AS reviews
                FROM reviews r
                WHERE r.congestion_level IS NOT NULL
                  AND %s
                GROUP BY r.congestion_level
                ORDER BY count(*) DESC, r.congestion_level
                """.formatted(REVIEW_BY_REAL_USER), params(), (rs, n) -> new CongestionRow(
                rs.getString("congestion_level"),
                rs.getLong("reviews")));
    }

    // ---------------------------------------------------------------- rows

    /** null일 수 있는 집계값. 대상이 0건이면 평균·중앙값이 null로 내려온다 */
    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    public record WriterStats(long writers, long repeatWriters, long totalReviews) {}

    public record WeeklyActiveRow(LocalDate weekStart, long activeUsers) {}

    public record VisibleNow(long visibleReviews, Double medianAgeHours, Double avgAgeHours) {}

    public record DailyVisibleRow(LocalDateTime snapshotAt, long visibleReviews, Double medianAgeHours) {}

    public record ModerationStats(long checkedComments, long suspectComments) {}

    public record CategoryCount(String category, long count) {}

    public record ReportStats(long reportedReviews, long blindedReviews) {}

    public record SeoulAreaStats(long dataPoints, LocalDateTime lastSyncedAt) {}

    public record SigunguRow(String sido, String sigungu, long places, long reviews) {}

    public record PlaceCategoryRow(String category, long places, long reviews) {}

    public record HeatmapRow(int dayOfWeek, int hour, long reviews) {}

    public record CongestionRow(String congestionLevel, long reviews) {}
}
