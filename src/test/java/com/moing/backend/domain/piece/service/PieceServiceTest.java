package com.moing.backend.domain.piece.service;

import com.moing.backend.domain.block.repository.BlockRepository;
import com.moing.backend.domain.block.service.BlockService;
import com.moing.backend.domain.follow.repository.FollowRepository;
import com.moing.backend.domain.piece.dto.PieceListResponse;
import com.moing.backend.domain.piece.dto.PiecePinUpdateRequest;
import com.moing.backend.domain.piece.dto.PiecePinUpdateResponse;
import com.moing.backend.domain.piece.entity.Piece;
import com.moing.backend.domain.piece.entity.PieceVisibility;
import com.moing.backend.domain.piece.repository.PieceRepository;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.review.entity.CongestionLevel;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.domain.user.entity.SocialProvider;
import com.moing.backend.domain.user.entity.User;
import com.moing.backend.domain.user.repository.UserRepository;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 조각 목록 조회·고정 테스트.
 *
 * <p>목록 접근을 가르는 값은 친구 관계가 아니라 조각의 공개 범위다. 친구가 아니어도 PUBLIC
 * 조각은 보이고, PRIVATE 조각은 누구에게도 안 보인다.
 *
 * <p>board scope는 "지금"을 기준으로 72h를 끊으므로 Clock을 고정해 둔다.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:piece;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class PieceServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 12, 0);

    @Autowired private PieceRepository pieceRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private PlaceRepository placeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BlockRepository blockRepository;
    @Autowired private FollowRepository followRepository;

    private PieceService pieceService;
    private BlockService blockService;

    private Long viewer;
    private Long owner;

    @BeforeEach
    void setUp() {
        ZoneId zone = ZoneId.systemDefault();
        Clock clock = Clock.fixed(NOW.atZone(zone).toInstant(), zone);

        blockService = new BlockService(blockRepository, userRepository, followRepository);
        pieceService = new PieceService(pieceRepository, reviewRepository, placeRepository,
                userRepository, blockService, clock);

        pieceRepository.deleteAll();
        blockRepository.deleteAll();
        reviewRepository.deleteAll();

        viewer = saveUser("조회자").getId();
        owner = saveUser("보드주인").getId();
    }

    @Test
    @DisplayName("친구가 아니어도 전체 공개 조각은 보인다")
    void 친구가_아니어도_공개_조각은_보인다() {
        savePiece(owner, PieceVisibility.PUBLIC);

        // 친구 관계를 만들지 않았다
        assertThat(followRepository.findByFollowerIdAndFollowingId(viewer, owner)).isEmpty();

        PieceListResponse response = pieceService.getUserPieces(viewer, owner, null);

        assertThat(response.pieces()).hasSize(1);
        assertThat(response.totalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("비공개 조각은 친구 여부와 무관하게 타인에게 안 보인다")
    void 비공개_조각은_안_보인다() {
        savePiece(owner, PieceVisibility.PUBLIC);
        savePiece(owner, PieceVisibility.PRIVATE);

        assertThat(pieceService.getUserPieces(viewer, owner, null).pieces()).hasSize(1);
    }

    @Test
    @DisplayName("타인 보드 응답에는 visibility를 싣지 않는다")
    void 타인_보드에는_공개범위를_싣지_않는다() {
        savePiece(owner, PieceVisibility.PUBLIC);

        assertThat(pieceService.getUserPieces(viewer, owner, null).pieces().get(0).visibility())
                .isNull();
    }

    @Test
    @DisplayName("본인 보드는 비공개 조각까지 보인다")
    void 본인_보드는_전부_보인다() {
        savePiece(owner, PieceVisibility.PUBLIC);
        savePiece(owner, PieceVisibility.PRIVATE);

        assertThat(pieceService.getMyPieces(owner, null).pieces()).hasSize(2);
    }

    @Test
    @DisplayName("차단 관계가 있으면 USER_ACCESS_DENIED")
    void 차단하면_막힌다() {
        savePiece(owner, PieceVisibility.PUBLIC);
        blockService.block(viewer, owner);

        assertThatThrownBy(() -> pieceService.getUserPieces(viewer, owner, null))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("나를 차단한 사용자의 보드도 막힌다 (양방향)")
    void 나를_차단해도_막힌다() {
        savePiece(owner, PieceVisibility.PUBLIC);
        blockService.block(owner, viewer);

        assertThatThrownBy(() -> pieceService.getUserPieces(viewer, owner, null))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("없는 사용자의 보드는 USER_NOT_FOUND")
    void 없는_사용자는_404다() {
        assertThatThrownBy(() -> pieceService.getUserPieces(viewer, 9999L, null))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    // --- 고정 ---

    @Test
    @DisplayName("고정하면 보낸 배열 순서대로 1부터 매겨진다")
    void 고정_순서는_배열_순서다() {
        Long first = savePiece(owner, PieceVisibility.PUBLIC).getId();
        Long second = savePiece(owner, PieceVisibility.PUBLIC).getId();
        Long third = savePiece(owner, PieceVisibility.PUBLIC).getId();

        PiecePinUpdateResponse response = pin(owner, third, first, second);

        assertThat(response.pinnedCount()).isEqualTo(3);
        assertThat(response.pins()).extracting(PiecePinUpdateResponse.PinItem::pieceId)
                .containsExactly(third, first, second);
        assertThat(response.pins()).extracting(PiecePinUpdateResponse.PinItem::pinOrder)
                .containsExactly((short) 1, (short) 2, (short) 3);

        assertThat(pinOrderOf(third)).isEqualTo((short) 1);
        assertThat(pinOrderOf(first)).isEqualTo((short) 2);
        assertThat(pinOrderOf(second)).isEqualTo((short) 3);
    }

    @Test
    @DisplayName("비공개 조각도 고정할 수 있다")
    void 비공개도_고정된다() {
        Long privatePiece = savePiece(owner, PieceVisibility.PRIVATE).getId();

        assertThat(pin(owner, privatePiece).pinnedCount()).isEqualTo(1);
        assertThat(pinOrderOf(privatePiece)).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("다시 호출하면 기존 고정은 풀리고 새 목록으로 교체된다")
    void 다시_호출하면_교체된다() {
        Long before = savePiece(owner, PieceVisibility.PUBLIC).getId();
        Long after = savePiece(owner, PieceVisibility.PUBLIC).getId();

        pin(owner, before);
        pin(owner, after);

        assertThat(pinOrderOf(before)).isNull();
        assertThat(pinOrderOf(after)).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("빈 배열이면 전체 해제된다")
    void 빈_배열은_전체_해제다() {
        Long first = savePiece(owner, PieceVisibility.PUBLIC).getId();
        Long second = savePiece(owner, PieceVisibility.PUBLIC).getId();
        pin(owner, first, second);

        PiecePinUpdateResponse response = pin(owner);

        assertThat(response.pinnedCount()).isZero();
        assertThat(response.pins()).isEmpty();
        assertThat(pinOrderOf(first)).isNull();
        assertThat(pinOrderOf(second)).isNull();
    }

    @Test
    @DisplayName("고정 슬롯(3개)을 넘기면 INVALID_PIECE_PIN")
    void 네_개는_400이다() {
        Long first = savePiece(owner, PieceVisibility.PUBLIC).getId();
        Long second = savePiece(owner, PieceVisibility.PUBLIC).getId();
        Long third = savePiece(owner, PieceVisibility.PUBLIC).getId();
        Long fourth = savePiece(owner, PieceVisibility.PUBLIC).getId();

        assertThatThrownBy(() -> pin(owner, first, second, third, fourth))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PIECE_PIN);
    }

    @Test
    @DisplayName("같은 조각을 두 번 보내면 INVALID_PIECE_PIN")
    void 중복_아이디는_400이다() {
        Long piece = savePiece(owner, PieceVisibility.PUBLIC).getId();

        assertThatThrownBy(() -> pin(owner, piece, piece))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PIECE_PIN);
    }

    @Test
    @DisplayName("남의 조각이 섞이면 NOT_OWN_PIECE이고 기존 고정은 그대로다")
    void 남의_조각이_섞이면_전체가_무산된다() {
        Long mine = savePiece(owner, PieceVisibility.PUBLIC).getId();
        Long another = savePiece(owner, PieceVisibility.PUBLIC).getId();
        Long others = savePiece(viewer, PieceVisibility.PUBLIC).getId();
        pin(owner, mine);

        assertThatThrownBy(() -> pin(owner, another, others))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_OWN_PIECE);

        // 검증이 끝난 뒤에야 손을 대므로 기존 고정이 풀리거나 새 고정이 끼어들지 않는다
        assertThat(pinOrderOf(mine)).isEqualTo((short) 1);
        assertThat(pinOrderOf(another)).isNull();
        assertThat(pinOrderOf(others)).isNull();
    }

    @Test
    @DisplayName("없는 조각 id도 남의 조각과 같이 NOT_OWN_PIECE")
    void 없는_조각도_거절된다() {
        assertThatThrownBy(() -> pin(owner, 9999L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_OWN_PIECE);
    }

    // --- 목록의 고정 필드 ---

    @Test
    @DisplayName("목록에 is_pinned와 pin_order가 실린다")
    void 목록에_고정_정보가_실린다() {
        Long pinned = savePiece(owner, PieceVisibility.PUBLIC).getId();
        Long plain = savePiece(owner, PieceVisibility.PUBLIC).getId();
        pin(owner, pinned);

        List<PieceListResponse.PieceItem> items = pieceService.getMyPieces(owner, null).pieces();

        assertThat(itemOf(items, pinned).isPinned()).isTrue();
        assertThat(itemOf(items, pinned).pinOrder()).isEqualTo((short) 1);
        assertThat(itemOf(items, plain).isPinned()).isFalse();
        assertThat(itemOf(items, plain).pinOrder()).isNull();
    }

    @Test
    @DisplayName("타인 보드에서 빠진 비공개 고정 조각 때문에 번호가 비지 않는다")
    void 타인_보드의_고정_번호는_다시_매겨진다() {
        Long open = savePiece(owner, PieceVisibility.PUBLIC).getId();
        Long secret = savePiece(owner, PieceVisibility.PRIVATE).getId();
        Long another = savePiece(owner, PieceVisibility.PUBLIC).getId();
        // 저장값은 1(open), 2(secret), 3(another)
        pin(owner, open, secret, another);

        List<PieceListResponse.PieceItem> mine = pieceService.getMyPieces(owner, null).pieces();
        assertThat(itemOf(mine, secret).pinOrder()).isEqualTo((short) 2);
        assertThat(itemOf(mine, another).pinOrder()).isEqualTo((short) 3);

        List<PieceListResponse.PieceItem> theirs =
                pieceService.getUserPieces(viewer, owner, null).pieces();

        assertThat(theirs).extracting(PieceListResponse.PieceItem::id)
                .containsExactlyInAnyOrder(open, another);
        assertThat(itemOf(theirs, open).pinOrder()).isEqualTo((short) 1);
        // 저장값은 3이지만 2로 당겨진다
        assertThat(itemOf(theirs, another).pinOrder()).isEqualTo((short) 2);
    }

    // --- scope ---

    @Test
    @DisplayName("scope를 생략하면 기간과 무관하게 전체가 실린다")
    void all은_전체다() {
        savePiece(owner, PieceVisibility.PUBLIC, NOW.minusHours(1));
        savePiece(owner, PieceVisibility.PUBLIC, NOW.minusDays(30));

        assertThat(pieceService.getMyPieces(owner, null).pieces()).hasSize(2);
        assertThat(pieceService.getMyPieces(owner, "all").pieces()).hasSize(2);
    }

    @Test
    @DisplayName("board는 72시간 이내 조각만 싣는다")
    void board는_최근_조각만_싣는다() {
        Long recent = savePiece(owner, PieceVisibility.PUBLIC, NOW.minusHours(1)).getId();
        savePiece(owner, PieceVisibility.PUBLIC, NOW.minusDays(30));

        PieceListResponse response = pieceService.getMyPieces(owner, "board");

        assertThat(response.pieces()).extracting(PieceListResponse.PieceItem::id)
                .containsExactly(recent);
        assertThat(response.totalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("72시간이 지난 조각도 고정했으면 board에 남는다")
    void 고정하면_기간이_지나도_남는다() {
        Long old = savePiece(owner, PieceVisibility.PUBLIC, NOW.minusDays(30)).getId();
        savePiece(owner, PieceVisibility.PUBLIC, NOW.minusDays(10));
        pin(owner, old);

        List<PieceListResponse.PieceItem> items = pieceService.getMyPieces(owner, "board").pieces();

        assertThat(items).extracting(PieceListResponse.PieceItem::id).containsExactly(old);
        assertThat(itemOf(items, old).isPinned()).isTrue();
    }

    @Test
    @DisplayName("board 경계값 — 정확히 72시간 전은 포함, 그보다 조금 전은 제외")
    void board_경계값() {
        Long onEdge = savePiece(owner, PieceVisibility.PUBLIC, NOW.minusHours(72)).getId();
        savePiece(owner, PieceVisibility.PUBLIC, NOW.minusHours(72).minusMinutes(1));

        assertThat(pieceService.getMyPieces(owner, "board").pieces())
                .extracting(PieceListResponse.PieceItem::id)
                .containsExactly(onEdge);
    }

    @Test
    @DisplayName("타인 보드도 PUBLIC만 추린 뒤 board 필터가 걸린다")
    void 타인_보드의_board_scope() {
        Long recentOpen = savePiece(owner, PieceVisibility.PUBLIC, NOW.minusHours(1)).getId();
        Long oldSecret = savePiece(owner, PieceVisibility.PRIVATE, NOW.minusDays(30)).getId();
        savePiece(owner, PieceVisibility.PUBLIC, NOW.minusDays(30));
        pin(owner, oldSecret);

        PieceListResponse response = pieceService.getUserPieces(viewer, owner, "board");

        assertThat(response.pieces()).extracting(PieceListResponse.PieceItem::id)
                .containsExactly(recentOpen);
        assertThat(response.totalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("total_count는 필터 후 실제로 실린 개수다")
    void total_count는_필터_후_개수다() {
        savePiece(owner, PieceVisibility.PUBLIC, NOW.minusHours(1));
        savePiece(owner, PieceVisibility.PUBLIC, NOW.minusHours(2));
        savePiece(owner, PieceVisibility.PUBLIC, NOW.minusDays(30));
        savePiece(owner, PieceVisibility.PUBLIC, NOW.minusDays(40));

        PieceListResponse response = pieceService.getMyPieces(owner, "board");

        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.pieces()).hasSize(2);
        assertThat(pieceService.getMyPieces(owner, null).totalCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("모르는 scope는 INVALID_PIECE_SCOPE")
    void 잘못된_scope는_400이다() {
        savePiece(owner, PieceVisibility.PUBLIC);

        assertThatThrownBy(() -> pieceService.getMyPieces(owner, "pinned"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PIECE_SCOPE);

        assertThatThrownBy(() -> pieceService.getUserPieces(viewer, owner, "pinned"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PIECE_SCOPE);
    }

    private PiecePinUpdateResponse pin(Long userId, Long... pieceIds) {
        return pieceService.updatePins(userId, new PiecePinUpdateRequest(List.of(pieceIds)));
    }

    private Short pinOrderOf(Long pieceId) {
        return pieceRepository.findById(pieceId).orElseThrow().getPinOrder();
    }

    private PieceListResponse.PieceItem itemOf(List<PieceListResponse.PieceItem> items, Long id) {
        return items.stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    private User saveUser(String nickname) {
        return userRepository.save(User.builder()
                .socialProvider(SocialProvider.KAKAO)
                .socialId("social-" + nickname)
                .nickname(nickname)
                .build());
    }

    private Piece savePiece(Long userId, PieceVisibility visibility) {
        return savePiece(userId, visibility, NOW);
    }

    /**
     * 조각 하나와 그 원본 리뷰를 만든다.
     *
     * <p>board scope의 기준 시각은 리뷰의 created_at인데 @CreatedDate라 넣을 수가 없어,
     * 저장한 뒤 값을 바꿔 끼운다 (updatable=false라 UPDATE로 새어 나가지 않는다).
     */
    private Piece savePiece(Long userId, PieceVisibility visibility, LocalDateTime reviewCreatedAt) {
        Review review = reviewRepository.save(Review.builder()
                .userId(userId)
                .placeId(10L)
                .congestionLevel(CongestionLevel.RELAXED)
                .imageUrl("https://img/original/1.jpg")
                .build());
        ReflectionTestUtils.setField(review, "createdAt", reviewCreatedAt);

        return pieceRepository.save(Piece.builder()
                .userId(userId)
                .reviewId(review.getId())
                .imageUrl("https://img/piece.png")
                .visibility(visibility)
                .build());
    }
}
