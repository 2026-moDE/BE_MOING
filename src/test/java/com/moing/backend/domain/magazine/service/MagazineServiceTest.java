package com.moing.backend.domain.magazine.service;

import com.moing.backend.domain.congestion.dto.CongestionResponse;
import com.moing.backend.domain.congestion.service.CongestionService;
import com.moing.backend.domain.magazine.dto.MagazineDetailResponse;
import com.moing.backend.domain.magazine.dto.MagazineListResponse;
import com.moing.backend.domain.magazine.entity.Magazine;
import com.moing.backend.domain.magazine.entity.MagazineItem;
import com.moing.backend.domain.magazine.entity.MagazineRule;
import com.moing.backend.domain.magazine.repository.MagazineItemRepository;
import com.moing.backend.domain.magazine.repository.MagazineRepository;
import com.moing.backend.domain.place.entity.Place;
import com.moing.backend.domain.place.entity.PlaceCategory;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.review.entity.CongestionLevel;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.entity.Visibility;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.domain.user.entity.SocialProvider;
import com.moing.backend.domain.user.entity.User;
import com.moing.backend.domain.user.repository.UserRepository;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 홈 바텀시트 매거진 조회 테스트.
 *
 * <p>노출 조건이 시각과 날씨로 갈려서, 둘 다 고정해 놓고 검증한다.
 */
class MagazineServiceTest {

    private static final double LAT = 37.54297;
    private static final double LNG = 127.05660;

    // 성수카페거리에서 약 740m 떨어진 좌표
    private static final BigDecimal NEAR_LAT = new BigDecimal("37.5496000");
    private static final BigDecimal NEAR_LNG = new BigDecimal("127.0566000");

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 12, 9, 43, 58);

    private MagazineRepository magazineRepository;
    private MagazineItemRepository magazineItemRepository;
    private ReviewRepository reviewRepository;
    private PlaceRepository placeRepository;
    private UserRepository userRepository;
    private CongestionService congestionService;

    @BeforeEach
    void setUp() {
        magazineRepository = mock(MagazineRepository.class);
        magazineItemRepository = mock(MagazineItemRepository.class);
        reviewRepository = mock(ReviewRepository.class);
        placeRepository = mock(PlaceRepository.class);
        userRepository = mock(UserRepository.class);
        congestionService = mock(CongestionService.class);
    }

    /** @param kstHour 조회 시각(KST) — LATE_NIGHT 노출 조건이 본다 */
    private MagazineService service(int kstHour) {
        Clock clock = Clock.fixed(
                LocalDateTime.of(2026, 10, 13, kstHour, 0).atZone(KST).toInstant(), KST);
        return new MagazineService(magazineRepository, magazineItemRepository, reviewRepository,
                placeRepository, userRepository, congestionService, clock);
    }

    private MagazineService service() {
        return service(12);   // 정오 — LATE_NIGHT가 안 보이는 시각
    }

    // ---------- 목록 ----------

    @Test
    @DisplayName("cover_image_url은 첫 번째 아이템 리뷰의 썸네일이다")
    void 커버는_첫_아이템_썸네일이다() {
        Magazine magazine = magazine(7L, MagazineRule.TODAY);
        givenMagazines(magazine);
        givenItems(item(7L, 101L, 0), item(7L, 102L, 1), item(7L, 103L, 2));
        givenReviews(review(101L, 1L), review(102L, 2L), review(103L, 3L));

        List<MagazineListResponse.MagazineItem> cards = service().getMagazines(LAT, LNG, null).magazines();

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).id()).isEqualTo(7L);
        assertThat(cards.get(0).title()).isEqualTo("오늘 올라온 것들");
        // 두 번째·세 번째가 아니라 sort_order 0번 리뷰의 썸네일
        assertThat(cards.get(0).coverImageUrl()).isEqualTo("https://cdn/thumbnail/101.jpg");
        assertThat(cards.get(0).itemCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("비가 오지 않으면 RAINY_INDOOR 묶음은 빠진다")
    void 비가_안_오면_RAINY_INDOOR는_빠진다() {
        givenMagazines(magazine(1L, MagazineRule.TODAY), magazine(2L, MagazineRule.RAINY_INDOOR));
        givenItems(item(1L, 101L, 0), item(1L, 102L, 1), item(2L, 201L, 0), item(2L, 202L, 1));
        givenReviews(review(101L, 1L), review(102L, 2L), review(201L, 3L), review(202L, 4L));
        givenWeather("없음");

        assertThat(service().getMagazines(LAT, LNG, null).magazines())
                .extracting(MagazineListResponse.MagazineItem::id)
                .containsExactly(1L);
    }

    @Test
    @DisplayName("비가 오면 RAINY_INDOOR 묶음이 보인다")
    void 비가_오면_RAINY_INDOOR가_보인다() {
        givenMagazines(magazine(2L, MagazineRule.RAINY_INDOOR));
        givenItems(item(2L, 201L, 0), item(2L, 202L, 1));
        givenReviews(review(201L, 3L), review(202L, 4L));
        givenWeather("비");

        assertThat(service().getMagazines(LAT, LNG, null).magazines())
                .extracting(MagazineListResponse.MagazineItem::id)
                .containsExactly(2L);
    }

    @Test
    @DisplayName("날씨를 모르면 RAINY_INDOOR 묶음은 빠진다")
    void 날씨를_모르면_RAINY_INDOOR는_빠진다() {
        givenMagazines(magazine(2L, MagazineRule.RAINY_INDOOR));
        when(congestionService.getNearbyWeather(anyDouble(), anyDouble())).thenReturn(null);

        // 맑은 날 "비 오는 날" 묶음이 뜨는 쪽이 더 어색하다
        assertThat(service().getMagazines(LAT, LNG, null).magazines()).isEmpty();
    }

    @Test
    @DisplayName("오전에는 LATE_NIGHT 묶음이 빠지고 저녁부터 보인다")
    void 오전에는_LATE_NIGHT가_빠진다() {
        givenMagazines(magazine(3L, MagazineRule.LATE_NIGHT));
        givenItems(item(3L, 301L, 0), item(3L, 302L, 1));
        givenReviews(review(301L, 1L), review(302L, 2L));

        assertThat(service(9).getMagazines(LAT, LNG, null).magazines()).isEmpty();
        assertThat(service(19).getMagazines(LAT, LNG, null).magazines()).isEmpty();
        assertThat(service(20).getMagazines(LAT, LNG, null).magazines()).hasSize(1);
        assertThat(service(23).getMagazines(LAT, LNG, null).magazines()).hasSize(1);
    }

    @Test
    @DisplayName("노출 조건이 ALWAYS면 날씨를 묻지 않는다")
    void 날씨가_필요없으면_호출하지_않는다() {
        givenMagazines(magazine(1L, MagazineRule.TODAY));
        givenItems(item(1L, 101L, 0));
        givenReviews(review(101L, 1L));

        service().getMagazines(LAT, LNG, null);

        verify(congestionService, never()).getNearbyWeather(anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("priority 높은 순, 같으면 아이템 많은 순으로 내려준다")
    void priority와_아이템_수로_정렬한다() {
        // 같은 규칙으로 priority가 같은 묶음 두 개를 만들어 2차 정렬만 본다
        givenMagazines(magazine(1L, MagazineRule.CROWDED), magazine(2L, MagazineRule.CROWDED),
                magazine(3L, MagazineRule.TODAY));
        givenItems(item(1L, 101L, 0), item(1L, 102L, 1),
                item(2L, 201L, 0), item(2L, 202L, 1), item(2L, 203L, 2),
                item(3L, 301L, 0));
        givenReviews(review(101L, 1L), review(102L, 2L),
                review(201L, 3L), review(202L, 4L), review(203L, 5L),
                review(301L, 6L));

        assertThat(service().getMagazines(LAT, LNG, null).magazines())
                .extracting(MagazineListResponse.MagazineItem::id)
                .containsExactly(3L, 2L, 1L);   // TODAY(50) → CROWDED 3개 → CROWDED 2개
    }

    @Test
    @DisplayName("남은 리뷰가 없는 묶음은 목록에서도 빠진다")
    void 리뷰가_모두_사라진_묶음은_빠진다() {
        givenMagazines(magazine(1L, MagazineRule.TODAY));
        givenItems(item(1L, 101L, 0), item(1L, 102L, 1));
        givenReviews(blinded(review(101L, 1L)), blinded(review(102L, 2L)));

        // 열어도 404라 카드도 내리지 않는다
        assertThat(service().getMagazines(LAT, LNG, null).magazines()).isEmpty();
    }

    @Test
    @DisplayName("item_count는 블라인드된 리뷰를 빼고 센다")
    void item_count는_노출_가능한_것만_센다() {
        givenMagazines(magazine(1L, MagazineRule.TODAY));
        givenItems(item(1L, 101L, 0), item(1L, 102L, 1), item(1L, 103L, 2));
        givenReviews(review(101L, 1L), blinded(review(102L, 2L)), review(103L, 3L));

        assertThat(service().getMagazines(LAT, LNG, null).magazines().get(0).itemCount())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("limit으로 개수를 자른다")
    void limit으로_자른다() {
        givenMagazines(magazine(1L, MagazineRule.TODAY), magazine(2L, MagazineRule.CROWDED));
        givenItems(item(1L, 101L, 0), item(2L, 201L, 0));
        givenReviews(review(101L, 1L), review(201L, 2L));

        assertThat(service().getMagazines(LAT, LNG, 1).magazines()).hasSize(1);
    }

    @Test
    @DisplayName("좌표가 없으면 400으로 끊는다")
    void 좌표가_없으면_400이다() {
        assertThatThrownBy(() -> service().getMagazines(null, LNG, null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    // ---------- 상세 ----------

    @Test
    @DisplayName("sort_order 순서를 유지하고 거리를 계산한다")
    void 상세는_순서를_유지하고_거리를_센다() {
        givenMagazine(7L, MagazineRule.RELAXED_CAFE);
        givenDetailItems(7L, item(7L, 103L, 0), item(7L, 101L, 1), item(7L, 102L, 2));
        givenReviews(review(101L, 1L), review(102L, 2L), review(103L, 3L));
        givenPlace(1L, "리볼리 성수");
        givenUser(1L, "커피조아");

        MagazineDetailResponse response = service().getMagazineDetail(7L, LAT, LNG);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.title()).isEqualTo("지금 한산한 카페");
        // 아이템 등록 순서가 아니라 sort_order 순서
        assertThat(response.items()).extracting(MagazineDetailResponse.Item::reviewId)
                .containsExactly(103L, 101L, 102L);

        MagazineDetailResponse.Item first = response.items().get(0);
        assertThat(first.distance()).isNotNull();
        assertThat(first.distance()).isBetween(700, 780);
        assertThat(first.congestionLevel()).isEqualTo(CongestionLevel.RELAXED);
        assertThat(first.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("좌표를 주지 않으면 distance는 null이다")
    void 좌표가_없으면_distance는_null이다() {
        givenMagazine(7L, MagazineRule.TODAY);
        givenDetailItems(7L, item(7L, 101L, 0));
        givenReviews(review(101L, 1L));
        givenPlace(1L, "리볼리 성수");
        givenUser(1L, "커피조아");

        // 0으로 두면 "바로 옆"과 구분이 안 된다
        assertThat(service().getMagazineDetail(7L, null, null).items().get(0).distance()).isNull();
    }

    @Test
    @DisplayName("블라인드된 리뷰는 상세에서 빠진다")
    void 블라인드된_아이템은_빠진다() {
        givenMagazine(7L, MagazineRule.TODAY);
        givenDetailItems(7L, item(7L, 101L, 0), item(7L, 102L, 1), item(7L, 103L, 2));
        givenReviews(review(101L, 1L), blinded(review(102L, 2L)), review(103L, 3L));
        givenPlace(1L, "리볼리 성수");
        givenUser(1L, "커피조아");

        assertThat(service().getMagazineDetail(7L, LAT, LNG).items())
                .extracting(MagazineDetailResponse.Item::reviewId)
                .containsExactly(101L, 103L);
    }

    @Test
    @DisplayName("배치 이후 지워진 리뷰도 상세에서 빠진다")
    void 지워진_아이템은_빠진다() {
        givenMagazine(7L, MagazineRule.TODAY);
        givenDetailItems(7L, item(7L, 101L, 0), item(7L, 999L, 1));
        givenReviews(review(101L, 1L));   // 999는 조회 결과에 없다 = 하드 삭제됨
        givenPlace(1L, "리볼리 성수");
        givenUser(1L, "커피조아");

        assertThat(service().getMagazineDetail(7L, LAT, LNG).items())
                .extracting(MagazineDetailResponse.Item::reviewId)
                .containsExactly(101L);
    }

    @Test
    @DisplayName("친구 공개로 바뀐 리뷰는 상세에서 빠진다")
    void 친구_공개_리뷰는_빠진다() {
        givenMagazine(7L, MagazineRule.TODAY);
        givenDetailItems(7L, item(7L, 101L, 0), item(7L, 102L, 1));
        givenReviews(review(101L, 1L), friendsOnly(review(102L, 2L)));
        givenPlace(1L, "리볼리 성수");
        givenUser(1L, "커피조아");

        // 매거진은 불특정 사용자에게 노출되므로 읽는 쪽에서도 막는다
        assertThat(service().getMagazineDetail(7L, LAT, LNG).items())
                .extracting(MagazineDetailResponse.Item::reviewId)
                .containsExactly(101L);
    }

    @Test
    @DisplayName("리뷰가 모두 사라진 묶음은 404다")
    void 리뷰가_모두_사라지면_404다() {
        givenMagazine(7L, MagazineRule.TODAY);
        givenDetailItems(7L, item(7L, 101L, 0), item(7L, 102L, 1));
        givenReviews(blinded(review(101L, 1L)), blinded(review(102L, 2L)));

        assertThatThrownBy(() -> service().getMagazineDetail(7L, LAT, LNG))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.MAGAZINE_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 묶음 id는 404다")
    void 없는_묶음은_404다() {
        when(magazineRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getMagazineDetail(99L, LAT, LNG))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.MAGAZINE_NOT_FOUND);
    }

    @Test
    @DisplayName("탈퇴한 작성자와 사라진 장소는 자리만 비운다")
    void 작성자나_장소가_없어도_응답한다() {
        givenMagazine(7L, MagazineRule.TODAY);
        givenDetailItems(7L, item(7L, 101L, 0));
        givenReviews(review(101L, 1L));
        when(placeRepository.findAllById(anyList())).thenReturn(List.of());
        when(userRepository.findAllById(anyList())).thenReturn(List.of());

        MagazineDetailResponse.Item item = service().getMagazineDetail(7L, LAT, LNG).items().get(0);

        assertThat(item.place().id()).isEqualTo(1L);
        assertThat(item.place().name()).isNull();
        assertThat(item.user().nickname()).isEqualTo("알 수 없음");
    }

    // ---------- 픽스처 ----------

    private void givenMagazines(Magazine... magazines) {
        when(magazineRepository.findAllByOrderByPriorityDescIdAsc()).thenReturn(List.of(magazines));
    }

    private void givenMagazine(long id, MagazineRule rule) {
        when(magazineRepository.findById(id)).thenReturn(Optional.of(magazine(id, rule)));
    }

    private void givenItems(MagazineItem... items) {
        when(magazineItemRepository.findByMagazineIdInOrderByMagazineIdAscSortOrderAsc(anyList()))
                .thenReturn(List.of(items));
    }

    private void givenDetailItems(long magazineId, MagazineItem... items) {
        when(magazineItemRepository.findByMagazineIdOrderBySortOrderAsc(magazineId))
                .thenReturn(List.of(items));
    }

    private void givenReviews(Review... reviews) {
        when(reviewRepository.findAllById(anyList())).thenReturn(List.of(reviews));
    }

    private void givenWeather(String precipitationType) {
        when(congestionService.getNearbyWeather(anyDouble(), anyDouble())).thenReturn(
                new CongestionResponse.Weather(
                        20.0, 22.0, 17.0, 60, null, precipitationType, "안내 문구",
                        "흐림", 60, 40, "보통", 20, "보통", "보통", "대기질 문구",
                        0, "낮음", "자외선 문구", "06:37", "18:00", CREATED_AT));
    }

    private void givenPlace(long id, String name) {
        when(placeRepository.findAllById(anyList())).thenReturn(List.of(
                Place.builder()
                        .id(id)
                        .name(name)
                        .address("서울 성동구 아차산로")
                        .latitude(NEAR_LAT)
                        .longitude(NEAR_LNG)
                        .category(PlaceCategory.CAFE)
                        .build()));
    }

    private void givenUser(long id, String nickname) {
        User user = User.builder()
                .socialProvider(SocialProvider.KAKAO)
                .socialId("social-" + id)
                .nickname(nickname)
                .profileImageUrl("https://cdn/profile_original/" + id + ".jpg")
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        when(userRepository.findAllById(anyList())).thenReturn(List.of(user));
    }

    private Magazine magazine(long id, MagazineRule rule) {
        Magazine magazine = Magazine.of(rule);
        ReflectionTestUtils.setField(magazine, "id", id);
        return magazine;
    }

    private MagazineItem item(long magazineId, long reviewId, int sortOrder) {
        return MagazineItem.of(magazineId, reviewId, sortOrder);
    }

    private Review review(long id, long placeId) {
        Review review = Review.builder()
                .userId(1L)
                .placeId(placeId)
                .congestionLevel(CongestionLevel.RELAXED)
                .comment("자리 엄청 많아요~~")
                .imageUrl("https://cdn/original/" + id + ".jpg")
                .latitude(NEAR_LAT)
                .longitude(NEAR_LNG)
                .visibility(Visibility.PUBLIC)
                .build();
        ReflectionTestUtils.setField(review, "id", id);
        ReflectionTestUtils.setField(review, "createdAt", CREATED_AT);
        return review;
    }

    private Review blinded(Review review) {
        review.blind();
        return review;
    }

    private Review friendsOnly(Review review) {
        ReflectionTestUtils.setField(review, "visibility", Visibility.FRIENDS);
        return review;
    }
}
