package com.moing.backend.domain.magazine.service;

import com.moing.backend.domain.magazine.entity.Magazine;
import com.moing.backend.domain.magazine.entity.MagazineItem;
import com.moing.backend.domain.magazine.entity.MagazineRule;
import com.moing.backend.domain.magazine.repository.MagazineItemRepository;
import com.moing.backend.domain.magazine.repository.MagazineRepository;
import com.moing.backend.domain.place.entity.PlaceCategory;
import com.moing.backend.domain.review.entity.CongestionLevel;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.entity.Visibility;
import com.moing.backend.domain.review.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 매거진 묶음 생성 배치 테스트.
 */
class MagazineBatchServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 4, 0);

    private ReviewRepository reviewRepository;
    private MagazineRepository magazineRepository;
    private MagazineItemRepository magazineItemRepository;

    private final AtomicLong magazineIdSeq = new AtomicLong(1);
    private final List<MagazineItem> savedItems = new ArrayList<>();

    @BeforeEach
    void setUp() {
        reviewRepository = mock(ReviewRepository.class);
        magazineRepository = mock(MagazineRepository.class);
        magazineItemRepository = mock(MagazineItemRepository.class);
        savedItems.clear();

        // save()가 id를 붙여 돌려주는 흉내 — 아이템이 magazine_id를 받아야 한다
        when(magazineRepository.save(any(Magazine.class))).thenAnswer(inv -> {
            Magazine m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", magazineIdSeq.getAndIncrement());
            return m;
        });
        when(magazineItemRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<MagazineItem> items = inv.getArgument(0);
            savedItems.addAll(items);
            return items;
        });

        // 기본값: 어느 규칙도 후보가 없다. 테스트가 필요한 규칙만 채운다
        when(reviewRepository.findMagazineCandidates(anyList(), anyList(), any(), any(Pageable.class)))
                .thenReturn(List.of());
        when(reviewRepository.findMagazineCandidatesByCreatedHours(anyList(), any(Pageable.class)))
                .thenReturn(List.of());
    }

    private MagazineBatchService service(ZoneId jvmZone) {
        Clock clock = Clock.fixed(NOW.atZone(jvmZone).toInstant(), jvmZone);
        return new MagazineBatchService(reviewRepository, magazineRepository,
                magazineItemRepository, clock);
    }

    private MagazineBatchService service() {
        return service(KST);
    }

    @Test
    @DisplayName("재실행하면 기존 묶음과 아이템을 먼저 지운다")
    void 재실행하면_기존_데이터를_지운다() {
        givenCandidates(List.of(CongestionLevel.RELAXED), review(1L, 10L), review(2L, 20L));

        service().generate();

        // 아이템을 먼저 지워야 고아 행이 남지 않는다
        var order = inOrder(magazineItemRepository, magazineRepository);
        order.verify(magazineItemRepository).deleteAllInBatch();
        order.verify(magazineRepository).deleteAllInBatch();
    }

    @Test
    @DisplayName("후보가 2개 미만이면 묶음을 만들지 않는다")
    void 후보가_부족하면_만들지_않는다() {
        givenCandidates(List.of(CongestionLevel.RELAXED), review(1L, 10L));   // 1개뿐

        int created = service().generate();

        assertThat(created).isZero();
        verify(magazineRepository, never()).save(any(Magazine.class));
    }

    @Test
    @DisplayName("같은 장소의 리뷰는 하나만 담는다")
    void 같은_장소는_하나만_담는다() {
        // 장소 10에 리뷰 3개, 장소 20에 1개
        givenCandidates(List.of(CongestionLevel.RELAXED),
                review(1L, 10L), review(2L, 10L), review(3L, 10L), review(4L, 20L));

        service().generate();

        // 같은 카페 사진이 나란히 뜨면 큐레이션으로 보이지 않는다
        assertThat(savedItems).extracting(MagazineItem::getReviewId).containsExactly(1L, 4L);
    }

    @Test
    @DisplayName("한 묶음은 최대 5개까지만 담는다")
    void 최대_5개까지_담는다() {
        List<Review> candidates = new ArrayList<>();
        for (long i = 1; i <= 8; i++) {
            candidates.add(review(i, i * 10));   // 모두 다른 장소
        }
        givenCandidates(List.of(CongestionLevel.RELAXED), candidates.toArray(new Review[0]));

        service().generate();

        assertThat(savedItems).hasSize(5);
        assertThat(savedItems).extracting(MagazineItem::getSortOrder).containsExactly(0, 1, 2, 3, 4);
        // 입력이 최신순이라 앞에서 다섯 개
        assertThat(savedItems).extracting(MagazineItem::getReviewId).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    @DisplayName("규칙의 고정 헤드라인과 우선순위를 저장한다")
    void 헤드라인과_우선순위를_저장한다() {
        givenCandidates(List.of(CongestionLevel.RELAXED), review(1L, 10L), review(2L, 20L));

        service().generate();

        ArgumentCaptor<Magazine> captor = ArgumentCaptor.forClass(Magazine.class);
        verify(magazineRepository).save(captor.capture());

        Magazine saved = captor.getValue();
        assertThat(saved.getRuleKey()).isEqualTo(MagazineRule.RELAXED_CAFE);
        assertThat(saved.getTitle()).isEqualTo("지금 한산한 카페");
        assertThat(saved.getPriority()).isEqualTo(MagazineRule.RELAXED_CAFE.getPriority());
    }

    @Test
    @DisplayName("RELAXED_CAFE는 카페 + 여유만 찾는다")
    void RELAXED_CAFE_조건() {
        service().generate();

        verify(reviewRepository).findMagazineCandidates(
                eq(List.of(CongestionLevel.RELAXED)), eq(List.of(PlaceCategory.CAFE)),
                eq(null), any(Pageable.class));
    }

    @Test
    @DisplayName("RAINY_INDOOR는 실내 카테고리를 찾고 ETC는 넣지 않는다")
    void RAINY_INDOOR_조건() {
        service().generate();

        verify(reviewRepository).findMagazineCandidates(
                eq(MagazineRule.ANY_LEVEL), eq(MagazineRule.INDOOR_CATEGORIES),
                eq(null), any(Pageable.class));
        assertThat(MagazineRule.INDOOR_CATEGORIES).doesNotContain(PlaceCategory.ETC);
    }

    @Test
    @DisplayName("TODAY는 24시간 이내로 자른다")
    void TODAY_조건() {
        service().generate();

        verify(reviewRepository).findMagazineCandidates(
                eq(MagazineRule.ANY_LEVEL), eq(List.of(PlaceCategory.values())),
                eq(NOW.minusHours(24)), any(Pageable.class));
    }

    @Test
    @DisplayName("CROWDED는 붐빔만 찾는다")
    void CROWDED_조건() {
        service().generate();

        verify(reviewRepository).findMagazineCandidates(
                eq(List.of(CongestionLevel.VERY_CROWDED)), eq(List.of(PlaceCategory.values())),
                eq(null), any(Pageable.class));
    }

    @Test
    @DisplayName("LATE_NIGHT는 KST 22시를 서버 기준 시각으로 환산해 넘긴다")
    void LATE_NIGHT는_시각을_환산한다() {
        // 서버가 KST면 그대로 22·23시
        service(KST).generate();
        verify(reviewRepository).findMagazineCandidatesByCreatedHours(
                eq(List.of(22, 23)), any(Pageable.class));

        // 서버가 UTC면 created_at도 UTC로 쓰이므로 13·14시를 봐야 한다
        service(UTC).generate();
        verify(reviewRepository).findMagazineCandidatesByCreatedHours(
                eq(List.of(13, 14)), any(Pageable.class));
    }

    @Test
    @DisplayName("규칙마다 따로 묶음을 만든다")
    void 규칙마다_묶음을_만든다() {
        givenCandidates(List.of(CongestionLevel.RELAXED), review(1L, 10L), review(2L, 20L));
        givenCandidates(List.of(CongestionLevel.VERY_CROWDED), review(3L, 30L), review(4L, 40L));

        assertThat(service().generate()).isEqualTo(2);
    }

    /** 특정 혼잡도 조건으로 들어오는 조회에만 후보를 돌려준다 */
    private void givenCandidates(List<CongestionLevel> levels, Review... reviews) {
        when(reviewRepository.findMagazineCandidates(
                eq(levels), anyList(), any(), any(Pageable.class)))
                .thenReturn(List.of(reviews));
    }

    private Review review(long id, long placeId) {
        Review review = Review.builder()
                .userId(1L)
                .placeId(placeId)
                .congestionLevel(CongestionLevel.RELAXED)
                .imageUrl("https://cdn/original/" + id + ".jpg")
                .latitude(new BigDecimal("37.5429700"))
                .longitude(new BigDecimal("127.0566000"))
                .visibility(Visibility.PUBLIC)
                .build();
        ReflectionTestUtils.setField(review, "id", id);
        return review;
    }
}
