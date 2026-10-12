package com.moing.backend.domain.review.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.moing.backend.support.H2Functions;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 탐색 탭 네이티브 쿼리 테스트.
 *
 * <p>리뷰는 JdbcTemplate으로 직접 넣는다. {@code created_at}이 @CreatedDate라 엔티티로는
 * 과거 시각을 심을 수 없는데, 이 테스트의 핵심이 "오래된 리뷰도 나오는가"다.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:explore;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class ExploreReviewQueryTest {

    private static final long VIEWER_ID = 1L;

    // 성수카페거리 기준. 서울숲공원은 약 1km, 서울역은 약 7km 떨어져 있다
    private static final double LAT = 37.54297;
    private static final double LNG = 127.05660;
    private static final int RADIUS = 2_000;

    /** 쿼리의 하버사인과 같은 구(반지름 6,371,000m)에서 위도 1도의 거리(m) */
    private static final double METERS_PER_DEGREE = 2 * Math.PI * 6_371_000 / 360;

    private static final String SEED = "abc12345";

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // H2에는 md5()가 없다. 정렬 순서를 재현하는 게 목적이라 실제 MD5를 그대로 끼워 넣는다
        jdbcTemplate.execute("CREATE ALIAS IF NOT EXISTS md5 FOR \""
                + H2Functions.class.getName() + ".md5\"");
        jdbcTemplate.update("DELETE FROM reviews");
    }

    @Test
    @DisplayName("72시간이 지난 리뷰도 내려준다")
    void 오래된_리뷰도_포함한다() {
        insertReview(1L, LAT, LNG, LocalDateTime.now().minusHours(1));
        insertReview(2L, LAT, LNG, LocalDateTime.now().minusHours(100));
        insertReview(3L, LAT, LNG, LocalDateTime.now().minusDays(400));

        assertThat(exploreIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    @DisplayName("반경을 넘는 리뷰는 기간과 무관하게 제외한다")
    void 반경_밖은_제외한다() {
        insertReview(1L, LAT, LNG, LocalDateTime.now().minusHours(1));
        // 서울역 좌표 — 2km 밖이다
        insertReview(2L, 37.55659, 126.97303, LocalDateTime.now().minusHours(1));

        assertThat(exploreIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("바운딩 박스 모서리에 걸친 리뷰도 반경 밖이면 제외한다")
    void 박스_모서리는_반경으로_걸러낸다() {
        // 박스는 원을 감싸는 정사각형이라 대각선 방향으로 반경 밖이 들어온다.
        // 위도·경도를 각각 반경의 0.9배만큼 옮기면 박스 안이지만 거리는 약 2.5km다
        double latOffset = RADIUS * 0.9 / METERS_PER_DEGREE;
        double lngOffset = RADIUS * 0.9 / (METERS_PER_DEGREE * Math.cos(Math.toRadians(LAT)));

        insertReview(1L, LAT, LNG, LocalDateTime.now().minusHours(1));
        insertReview(2L, LAT + latOffset, LNG + lngOffset, LocalDateTime.now().minusHours(1));

        // 박스를 선필터로 쓰더라도 결과는 하버사인 기준 그대로여야 한다
        assertThat(exploreIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("박스 안이고 반경 안이면 경계 근처라도 포함한다")
    void 반경_경계_안은_포함한다() {
        // 정동 방향으로 반경의 0.9배 — 박스도 원도 안쪽이다
        double lngOffset = RADIUS * 0.9 / (METERS_PER_DEGREE * Math.cos(Math.toRadians(LAT)));

        insertReview(1L, LAT, LNG + lngOffset, LocalDateTime.now().minusDays(300));

        assertThat(exploreIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("정북·정남 경계 바로 안쪽 리뷰를 박스가 깎아내지 않는다")
    void 박스가_원을_깎지_않는다() {
        // 박스가 가장 타이트해지는 방향이 정북·정남이다. 반경보다 1m 안쪽이면 반드시 나와야 한다
        double latOffset = (RADIUS - 1) / METERS_PER_DEGREE;

        insertReview(1L, LAT + latOffset, LNG, LocalDateTime.now().minusHours(1));
        insertReview(2L, LAT - latOffset, LNG, LocalDateTime.now().minusHours(1));

        assertThat(exploreIds()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("사진이 없는 리뷰와 블라인드된 리뷰는 제외한다")
    void 사진이_없거나_블라인드면_제외한다() {
        insertReview(1L, LAT, LNG, LocalDateTime.now().minusHours(1));
        insert(2L, LAT, LNG, LocalDateTime.now().minusHours(1), null, false, "PUBLIC");
        insert(3L, LAT, LNG, LocalDateTime.now().minusHours(1), "https://img/3.jpg", true, "PUBLIC");

        assertThat(exploreIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("친구만 공개한 남의 리뷰는 기간과 무관하게 제외한다")
    void 친구_공개_리뷰는_제외한다() {
        // 작성자가 조회자가 아니고 친구 관계도 없다
        insert(1L, 99L, LAT, LNG, LocalDateTime.now().minusDays(200), "https://img/1.jpg", false, "FRIENDS");
        insert(2L, 99L, LAT, LNG, LocalDateTime.now().minusDays(200), "https://img/2.jpg", false, "PUBLIC");

        assertThat(exploreIds()).containsExactly(2L);
    }

    @Test
    @DisplayName("좌표가 없는 옛 리뷰는 거리 계산이 안 되어 빠진다")
    void 좌표가_없으면_빠진다() {
        insertReview(1L, LAT, LNG, LocalDateTime.now().minusHours(1));
        insert(2L, VIEWER_ID, null, null, LocalDateTime.now().minusHours(1), "https://img/2.jpg", false, "PUBLIC");

        assertThat(exploreIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("같은 seed면 순서가 재현되고 offset으로 이어진다")
    void 같은_seed면_순서가_같다() {
        for (long id = 1; id <= 5; id++) {
            insertReview(id, LAT, LNG, LocalDateTime.now().minusHours(id * 30));
        }

        List<Long> firstPage = page(2, 0);
        List<Long> secondPage = page(2, 2);

        // 다시 물어도 같은 순서여야 이미 본 사진이 다시 나오지 않는다
        assertThat(page(2, 0)).isEqualTo(firstPage);
        assertThat(firstPage).hasSize(2).doesNotContainAnyElementsOf(secondPage);
        assertThat(page(5, 0)).startsWith(firstPage.get(0), firstPage.get(1));
    }

    @Test
    @DisplayName("limit보다 한 건 더 받아 다음 페이지 유무를 알 수 있다")
    void 한_건_더_받아_다음_페이지를_안다() {
        for (long id = 1; id <= 3; id++) {
            insertReview(id, LAT, LNG, LocalDateTime.now().minusDays(id * 20));
        }

        // 서비스가 쓰는 방식 — limit + 1로 물어 초과분이 오면 다음 페이지가 있다
        assertThat(page(2 + 1, 0)).hasSize(3);
        assertThat(page(3 + 1, 0)).hasSize(3);
        // 마지막 페이지는 초과분이 없다
        assertThat(page(2 + 1, 2)).hasSize(1);
    }

    private List<Long> exploreIds() {
        return page(50, 0);
    }

    private List<Long> page(int limit, int offset) {
        return reviewRepository
                .findExploreReviewIds(LAT, LNG, RADIUS, VIEWER_ID, SEED, limit, offset)
                .stream()
                .map(row -> ((Number) row[0]).longValue())
                .toList();
    }

    private void insertReview(long id, double latitude, double longitude, LocalDateTime createdAt) {
        insert(id, VIEWER_ID, latitude, longitude, createdAt, "https://img/" + id + ".jpg", false, "PUBLIC");
    }

    private void insert(long id, Double latitude, Double longitude, LocalDateTime createdAt,
                        String imageUrl, boolean blinded, String visibility) {
        insert(id, VIEWER_ID, latitude, longitude, createdAt, imageUrl, blinded, visibility);
    }

    private void insert(long id, long userId, Double latitude, Double longitude, LocalDateTime createdAt,
                        String imageUrl, boolean blinded, String visibility) {
        jdbcTemplate.update("""
                INSERT INTO reviews
                  (id, user_id, place_id, congestion_level, image_url, latitude, longitude,
                   visibility, status, is_blinded, helpful_count, view_count, created_at)
                VALUES (?, ?, ?, 'RELAXED', ?, ?, ?, ?, 'ACTIVE', ?, 0, 0, ?)
                """,
                id, userId, 10L, imageUrl, latitude, longitude, visibility, blinded, createdAt);
    }
}
