package com.moing.backend.domain.piece.service;

import com.moing.backend.domain.block.repository.BlockRepository;
import com.moing.backend.domain.block.service.BlockService;
import com.moing.backend.domain.follow.repository.FollowRepository;
import com.moing.backend.domain.piece.dto.PieceListResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 타인 보드(조각 목록) 조회 테스트.
 *
 * <p>접근을 가르는 값은 친구 관계가 아니라 조각의 공개 범위다. 친구가 아니어도 PUBLIC
 * 조각은 보이고, PRIVATE 조각은 누구에게도 안 보인다.
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
        blockService = new BlockService(blockRepository, userRepository, followRepository);
        pieceService = new PieceService(pieceRepository, reviewRepository, placeRepository,
                userRepository, blockService);

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

        PieceListResponse response = pieceService.getUserPieces(viewer, owner);

        assertThat(response.pieces()).hasSize(1);
        assertThat(response.totalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("비공개 조각은 친구 여부와 무관하게 타인에게 안 보인다")
    void 비공개_조각은_안_보인다() {
        savePiece(owner, PieceVisibility.PUBLIC);
        savePiece(owner, PieceVisibility.PRIVATE);

        assertThat(pieceService.getUserPieces(viewer, owner).pieces()).hasSize(1);
    }

    @Test
    @DisplayName("타인 보드 응답에는 visibility를 싣지 않는다")
    void 타인_보드에는_공개범위를_싣지_않는다() {
        savePiece(owner, PieceVisibility.PUBLIC);

        assertThat(pieceService.getUserPieces(viewer, owner).pieces().get(0).visibility()).isNull();
    }

    @Test
    @DisplayName("본인 보드는 비공개 조각까지 보인다")
    void 본인_보드는_전부_보인다() {
        savePiece(owner, PieceVisibility.PUBLIC);
        savePiece(owner, PieceVisibility.PRIVATE);

        assertThat(pieceService.getMyPieces(owner).pieces()).hasSize(2);
    }

    @Test
    @DisplayName("차단 관계가 있으면 USER_ACCESS_DENIED")
    void 차단하면_막힌다() {
        savePiece(owner, PieceVisibility.PUBLIC);
        blockService.block(viewer, owner);

        assertThatThrownBy(() -> pieceService.getUserPieces(viewer, owner))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("나를 차단한 사용자의 보드도 막힌다 (양방향)")
    void 나를_차단해도_막힌다() {
        savePiece(owner, PieceVisibility.PUBLIC);
        blockService.block(owner, viewer);

        assertThatThrownBy(() -> pieceService.getUserPieces(viewer, owner))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("없는 사용자의 보드는 USER_NOT_FOUND")
    void 없는_사용자는_404다() {
        assertThatThrownBy(() -> pieceService.getUserPieces(viewer, 9999L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    private User saveUser(String nickname) {
        return userRepository.save(User.builder()
                .socialProvider(SocialProvider.KAKAO)
                .socialId("social-" + nickname)
                .nickname(nickname)
                .build());
    }

    private void savePiece(Long userId, PieceVisibility visibility) {
        Review review = reviewRepository.save(Review.builder()
                .userId(userId)
                .placeId(10L)
                .congestionLevel(CongestionLevel.RELAXED)
                .imageUrl("https://img/original/1.jpg")
                .build());

        pieceRepository.save(Piece.builder()
                .userId(userId)
                .reviewId(review.getId())
                .imageUrl("https://img/piece.png")
                .visibility(visibility)
                .build());
    }
}
