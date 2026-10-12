package com.moing.backend.domain.magazine.repository;

import com.moing.backend.domain.magazine.entity.Magazine;
import com.moing.backend.domain.magazine.entity.MagazineItem;
import com.moing.backend.domain.magazine.entity.MagazineRule;
import com.moing.backend.domain.place.entity.PlaceCategory;
import com.moing.backend.domain.review.entity.CongestionLevel;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.repository.ReviewRepository;
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
 * 매거진 쿼리 테스트.
 *
 * <p>JPQL과 파생 쿼리는 컴파일이 아니라 부팅 시점에 검증되므로, 컴파일만 통과해도
 * 런타임에 깨질 수 있다. 특히 배치 후보 쿼리는 {@code HOUR()}와 서브쿼리를 쓴다.
 *
 * <p>리뷰는 JdbcTemplate으로 직접 넣는다 — {@code created_at}이 @CreatedDate라
 * 엔티티로는 특정 시각을 심을 수 없는데, 시각대 조건이 이 테스트의 대상이다.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:magazine;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class MagazineQueryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 12, 0);

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private MagazineRepository magazineRepository;

    @Autowired
    private MagazineItemRepository magazineItemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM magazine_items");
        jdbcTemplate.update("DELETE FROM magazines");
        jdbcTemplate.update("DELETE FROM reviews");
        jdbcTemplate.update("DELETE FROM places");

        // 카페 1, 음식점 2, 공원(ETC) 3
        insertPlace(1L, "리볼리 성수", "CAFE");
        insertPlace(2L, "성수 식당", "RESTAURANT");
        insertPlace(3L, "서울숲", "ETC");
    }

    @Test
    @DisplayName("카테고리와 혼잡도로 후보를 고른다")
    void 카테고리와_혼잡도로_고른다() {
        insertReview(1L, 1L, "RELAXED", NOW);          // 카페 + 여유
        insertReview(2L, 1L, "VERY_CROWDED", NOW);     // 카페 + 붐빔
        insertReview(3L, 2L, "RELAXED", NOW);          // 음식점 + 여유

        List<Review> found = reviewRepository.findMagazineCandidates(
                List.of(CongestionLevel.RELAXED), List.of(PlaceCategory.CAFE), null, limit());

        assertThat(found).extracting(Review::getId).containsExactly(1L);
    }

    @Test
    @DisplayName("실내 카테고리만 고르고 ETC는 제외한다")
    void 실내_카테고리만_고른다() {
        insertReview(1L, 1L, "RELAXED", NOW);   // 카페
        insertReview(2L, 2L, "RELAXED", NOW);   // 음식점
        insertReview(3L, 3L, "RELAXED", NOW);   // ETC

        List<Review> found = reviewRepository.findMagazineCandidates(
                MagazineRule.ANY_LEVEL, MagazineRule.INDOOR_CATEGORIES, null, limit());

        assertThat(found).extracting(Review::getId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("since를 주면 그 이후 리뷰만, 안 주면 기간 제한이 없다")
    void since로_기간을_자른다() {
        insertReview(1L, 1L, "RELAXED", NOW.minusHours(1));
        insertReview(2L, 2L, "RELAXED", NOW.minusDays(5));

        assertThat(reviewRepository.findMagazineCandidates(
                MagazineRule.ANY_LEVEL, allCategories(), NOW.minusHours(24), limit()))
                .extracting(Review::getId).containsExactly(1L);

        assertThat(reviewRepository.findMagazineCandidates(
                MagazineRule.ANY_LEVEL, allCategories(), null, limit()))
                .extracting(Review::getId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("블라인드·친구 공개·사진 없는 리뷰는 후보에서 빠진다")
    void 노출_불가_리뷰는_빠진다() {
        insertReview(1L, 1L, "RELAXED", NOW);
        insert(2L, 1L, 2L, "RELAXED", NOW, "https://img/2.jpg", true, "PUBLIC");    // 블라인드
        insert(3L, 1L, 3L, "RELAXED", NOW, "https://img/3.jpg", false, "FRIENDS");  // 친구 공개
        insert(4L, 1L, 4L, "RELAXED", NOW, null, false, "PUBLIC");                  // 사진 없음

        List<Review> found = reviewRepository.findMagazineCandidates(
                MagazineRule.ANY_LEVEL, allCategories(), null, limit());

        assertThat(found).extracting(Review::getId).containsExactly(1L);
    }

    @Test
    @DisplayName("작성 시각대(hour)로 후보를 고른다")
    void 시각대로_고른다() {
        insertReview(1L, 1L, "RELAXED", NOW.withHour(22));
        insertReview(2L, 2L, "RELAXED", NOW.withHour(23));
        insertReview(3L, 3L, "RELAXED", NOW.withHour(13));

        List<Review> found = reviewRepository.findMagazineCandidatesByCreatedHours(
                List.of(22, 23), limit());

        assertThat(found).extracting(Review::getId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("후보는 최신순이고 limit으로 자른다")
    void 최신순으로_자른다() {
        insertReview(1L, 1L, "RELAXED", NOW.minusHours(3));
        insertReview(2L, 2L, "RELAXED", NOW.minusHours(1));
        insertReview(3L, 3L, "RELAXED", NOW.minusHours(2));

        List<Review> found = reviewRepository.findMagazineCandidates(
                MagazineRule.ANY_LEVEL, allCategories(), null, PageRequest.of(0, 2));

        assertThat(found).extracting(Review::getId).containsExactly(2L, 3L);
    }

    @Test
    @DisplayName("묶음은 priority 높은 순으로 읽고, 아이템은 sort_order 순으로 읽는다")
    void 묶음과_아이템_정렬() {
        Magazine crowded = magazineRepository.save(Magazine.of(MagazineRule.CROWDED));   // priority 10
        Magazine today = magazineRepository.save(Magazine.of(MagazineRule.TODAY));       // priority 50

        magazineItemRepository.saveAll(List.of(
                MagazineItem.of(today.getId(), 30L, 2),
                MagazineItem.of(today.getId(), 10L, 0),
                MagazineItem.of(today.getId(), 20L, 1)));

        assertThat(magazineRepository.findAllByOrderByPriorityDescIdAsc())
                .extracting(Magazine::getRuleKey)
                .containsExactly(MagazineRule.TODAY, MagazineRule.CROWDED);

        assertThat(magazineItemRepository.findByMagazineIdOrderBySortOrderAsc(today.getId()))
                .extracting(MagazineItem::getReviewId)
                .containsExactly(10L, 20L, 30L);

        assertThat(magazineItemRepository.findByMagazineIdInOrderByMagazineIdAscSortOrderAsc(
                List.of(today.getId(), crowded.getId())))
                .hasSize(3);
    }

    private PageRequest limit() {
        return PageRequest.of(0, 100);
    }

    private List<PlaceCategory> allCategories() {
        return List.of(PlaceCategory.values());
    }

    private void insertPlace(long id, String name, String category) {
        jdbcTemplate.update("""
                INSERT INTO places (id, name, address, latitude, longitude, category, is_active, created_at)
                VALUES (?, ?, '서울 성동구', 37.5429700, 127.0566000, ?, true, ?)
                """, id, name, category, NOW);
    }

    private void insertReview(long id, long placeId, String level, LocalDateTime createdAt) {
        insert(id, placeId, 1L, level, createdAt, "https://img/" + id + ".jpg", false, "PUBLIC");
    }

    private void insert(long id, long placeId, long userId, String level, LocalDateTime createdAt,
                        String imageUrl, boolean blinded, String visibility) {
        jdbcTemplate.update("""
                INSERT INTO reviews
                  (id, user_id, place_id, congestion_level, image_url, thumbnail_url,
                   latitude, longitude, visibility, status, is_blinded,
                   helpful_count, view_count, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 37.5429700, 127.0566000, ?, 'ACTIVE', ?, 0, 0, ?)
                """,
                id, userId, placeId, level, imageUrl,
                imageUrl == null ? null : imageUrl.replace("/img/", "/thumb/"),
                visibility, blinded, createdAt);
    }
}
