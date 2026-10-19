package com.moing.backend.domain.block.repository;

import com.moing.backend.domain.comment.entity.Comment;
import com.moing.backend.domain.comment.repository.CommentRepository;
import com.moing.backend.domain.place.entity.Place;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.support.H2Functions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 차단 조회 필터 테스트.
 *
 * <p>검증하는 것은 하나다 — <b>차단은 양방향으로 가린다.</b> 내가 차단한 사용자의 글도,
 * 나를 차단한 사용자의 글도 안 보인다. 한쪽만 막으면 차단당한 사람이 상대 글에 계속
 * 댓글을 달 수 있어 차단이 의미가 없다.
 *
 * <p>리뷰·댓글·차단은 JdbcTemplate으로 직접 넣는다. {@code created_at}이 @CreatedDate라
 * 엔티티로는 특정 시각을 심을 수 없는데, 72h 경계로 갈리는 쿼리가 대상에 있다.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:blockfilter;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class BlockFilterQueryTest {

    private static final long VIEWER = 1L;
    private static final long OTHER = 2L;
    private static final long THIRD = 3L;

    private static final long PLACE_ID = 10L;

    // 성수카페거리
    private static final double LAT = 37.54297;
    private static final double LNG = 127.05660;
    private static final int RADIUS = 2_000;

    private static final LocalDateTime NOW = LocalDateTime.now();
    private static final LocalDateTime SINCE = NOW.minusHours(72);

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // H2에는 md5()가 없다. 탐색 쿼리의 정렬 키라 실제 MD5를 끼워 넣는다
        jdbcTemplate.execute("CREATE ALIAS IF NOT EXISTS md5 FOR \""
                + H2Functions.class.getName() + ".md5\"");
        jdbcTemplate.update("DELETE FROM blocks");
        jdbcTemplate.update("DELETE FROM comments");
        jdbcTemplate.update("DELETE FROM reviews");
        jdbcTemplate.update("DELETE FROM places");
    }

    // ---------- 장소별 리뷰 목록 ----------

    @Test
    @DisplayName("내가 차단한 사용자의 리뷰는 현재 리뷰 목록에서 빠진다")
    void 차단한_사용자_리뷰는_안_보인다() {
        insertReview(1L, VIEWER, NOW.minusHours(1));
        insertReview(2L, OTHER, NOW.minusHours(1));

        assertThat(currentReviewIds()).containsExactly(2L, 1L);

        block(VIEWER, OTHER);

        assertThat(currentReviewIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("나를 차단한 사용자의 리뷰도 안 보인다 (양방향)")
    void 나를_차단한_사용자_리뷰도_안_보인다() {
        insertReview(1L, VIEWER, NOW.minusHours(1));
        insertReview(2L, OTHER, NOW.minusHours(1));

        // 차단한 쪽이 상대다 — 내가 만든 행이 아니어도 가려져야 한다
        block(OTHER, VIEWER);

        assertThat(currentReviewIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("차단과 무관한 제3자의 리뷰는 그대로 보인다")
    void 제3자_리뷰는_그대로다() {
        insertReview(1L, OTHER, NOW.minusHours(1));
        insertReview(2L, THIRD, NOW.minusHours(1));

        block(VIEWER, OTHER);

        assertThat(currentReviewIds()).containsExactly(2L);
    }

    @Test
    @DisplayName("차단을 해제하면 리뷰가 다시 보인다")
    void 해제하면_다시_보인다() {
        insertReview(1L, OTHER, NOW.minusHours(1));

        block(VIEWER, OTHER);
        assertThat(currentReviewIds()).isEmpty();

        jdbcTemplate.update("DELETE FROM blocks WHERE blocker_id = ? AND blocked_id = ?", VIEWER, OTHER);

        assertThat(currentReviewIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("과거 리뷰 목록도 같은 기준으로 가린다")
    void 과거_리뷰도_가린다() {
        insertReview(1L, OTHER, NOW.minusHours(100));
        insertReview(2L, THIRD, NOW.minusHours(100));

        block(OTHER, VIEWER);

        List<Long> ids = reviewRepository
                .findArchivedReviews(PLACE_ID, SINCE, null, VIEWER, PageRequest.of(0, 10))
                .stream().map(Review::getId).toList();
        assertThat(ids).containsExactly(2L);
    }

    @Test
    @DisplayName("리뷰 수도 목록과 같은 개수를 센다")
    void 리뷰_수가_목록과_맞는다() {
        insertReview(1L, OTHER, NOW.minusHours(1));
        insertReview(2L, THIRD, NOW.minusHours(1));

        block(VIEWER, OTHER);

        assertThat(reviewRepository.countVisibleCurrentReviews(PLACE_ID, SINCE, VIEWER))
                .isEqualTo(currentReviewIds().size());
    }

    // ---------- 탐색 피드 ----------

    @Test
    @DisplayName("탐색 피드에서도 양방향으로 가린다")
    void 탐색도_양방향으로_가린다() {
        insertReview(1L, THIRD, NOW.minusHours(1));
        insertReview(2L, OTHER, NOW.minusHours(1));

        assertThat(exploreIds()).containsExactlyInAnyOrder(1L, 2L);

        block(OTHER, VIEWER);

        assertThat(exploreIds()).containsExactly(1L);
    }

    // ---------- 장소 대표 사진 ----------

    @Test
    @DisplayName("대표 사진은 차단된 작성자의 리뷰를 건너뛰고 다음 사진을 고른다")
    void 대표_사진은_다음_사진으로_넘어간다() {
        // 최신이 차단 상대의 리뷰다. 메모리에서 걸러내면 사진이 있는데도 빈 칸이 된다
        insertReview(1L, THIRD, NOW.minusHours(5));
        insertReview(2L, OTHER, NOW.minusHours(1));

        block(VIEWER, OTHER);

        List<Review> top = reviewRepository.findTopWithImageByPlaceId(
                PLACE_ID, SINCE, VIEWER, PageRequest.of(0, 1));

        assertThat(top).hasSize(1);
        assertThat(top.get(0).getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("기간 제한 없는 대표 사진 조회도 같은 기준으로 넘어간다")
    void 전체_기간_대표_사진도_넘어간다() {
        insertReview(1L, THIRD, NOW.minusDays(300));
        insertReview(2L, OTHER, NOW.minusDays(200));

        block(OTHER, VIEWER);

        List<Review> top = reviewRepository.findTopWithImageAllTimeByPlaceId(
                PLACE_ID, VIEWER, PageRequest.of(0, 1));

        assertThat(top).hasSize(1);
        assertThat(top.get(0).getId()).isEqualTo(1L);
    }

    // ---------- 인기 장소 ----------

    @Test
    @DisplayName("차단으로 리뷰가 기준에 못 미치면 인기 장소에서 빠진다")
    void 인기_장소_집계에도_반영된다() {
        insertPlace(PLACE_ID);
        insertReview(1L, OTHER, NOW.minusHours(1));
        insertReview(2L, THIRD, NOW.minusHours(1));

        // 리뷰 2개 = 인기 장소 기준을 만족한다
        assertThat(hotPlaceIds()).containsExactly(PLACE_ID);

        block(VIEWER, OTHER);

        // 한 건이 가려져 기준에 못 미친다.
        // 여기서 빠지지 않으면 응답의 review_count와 노출 기준이 어긋난다
        assertThat(hotPlaceIds()).isEmpty();
    }

    // ---------- 댓글과 답글 ----------

    @Test
    @DisplayName("차단된 작성자의 댓글과 답글은 목록에서 빠진다")
    void 댓글과_답글을_가린다() {
        insertReview(1L, VIEWER, NOW.minusHours(1));
        insertComment(100L, 1L, THIRD, null);
        insertComment(101L, 1L, OTHER, null);
        insertComment(102L, 1L, OTHER, 100L);   // 차단 상대가 THIRD의 댓글에 단 답글
        insertComment(103L, 1L, THIRD, 100L);

        block(OTHER, VIEWER);

        List<Long> topLevel = commentRepository
                .findTopLevelComments(1L, null, VIEWER, PageRequest.of(0, 10))
                .stream().map(Comment::getId).toList();
        assertThat(topLevel).containsExactly(100L);

        List<Long> replies = commentRepository
                .findRepliesByParentIds(List.of(100L), VIEWER)
                .stream().map(Comment::getId).toList();
        assertThat(replies).containsExactly(103L);
    }

    @Test
    @DisplayName("댓글 수는 목록에 보이는 개수와 같다")
    void 댓글_수가_목록과_맞는다() {
        insertReview(1L, VIEWER, NOW.minusHours(1));
        insertComment(100L, 1L, THIRD, null);
        insertComment(101L, 1L, OTHER, null);
        insertComment(102L, 1L, OTHER, 100L);

        block(VIEWER, OTHER);

        // 차단된 작성자의 댓글을 세면 "댓글 3개"를 눌렀는데 1개만 보이는 화면이 된다
        assertThat(commentRepository.countByReviewId(1L, VIEWER)).isEqualTo(1L);
        assertThat(commentRepository.countMapByReviewIds(List.of(1L), VIEWER))
                .containsEntry(1L, 1L);
    }

    // ---------- 헬퍼 ----------

    private List<Long> currentReviewIds() {
        return reviewRepository
                .findCurrentReviews(PLACE_ID, SINCE, null, VIEWER, PageRequest.of(0, 10))
                .stream().map(Review::getId).toList();
    }

    private List<Long> exploreIds() {
        return reviewRepository
                .findExploreReviewIds(LAT, LNG, RADIUS, VIEWER, "seed1234", 50, 0)
                .stream().map(row -> ((Number) row[0]).longValue()).toList();
    }

    private List<Long> hotPlaceIds() {
        return placeRepository
                .findHotPlaces(LAT, LNG, SINCE, 2, VIEWER, PageRequest.of(0, 5))
                .stream().map(Place::getId).toList();
    }

    private void block(long blockerId, long blockedId) {
        jdbcTemplate.update(
                "INSERT INTO blocks (blocker_id, blocked_id, created_at) VALUES (?, ?, ?)",
                blockerId, blockedId, NOW);
    }

    private void insertPlace(long id) {
        jdbcTemplate.update("""
                INSERT INTO places (id, name, address, latitude, longitude, is_active, created_at)
                VALUES (?, '성수카페', '서울 성동구', ?, ?, true, ?)
                """, id, LAT, LNG, NOW);
    }

    private void insertReview(long id, long userId, LocalDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO reviews
                  (id, user_id, place_id, congestion_level, image_url, latitude, longitude,
                   visibility, status, is_blinded, helpful_count, view_count, created_at)
                VALUES (?, ?, ?, 'RELAXED', ?, ?, ?, 'PUBLIC', 'ACTIVE', false, 0, 0, ?)
                """,
                id, userId, PLACE_ID, "https://img/" + id + ".jpg", LAT, LNG, createdAt);
    }

    private void insertComment(long id, long reviewId, long userId, Long parentId) {
        jdbcTemplate.update("""
                INSERT INTO comments
                  (id, review_id, user_id, parent_id, content, is_secret, is_deleted, created_at)
                VALUES (?, ?, ?, ?, '댓글', false, false, ?)
                """, id, reviewId, userId, parentId, NOW);
    }
}
