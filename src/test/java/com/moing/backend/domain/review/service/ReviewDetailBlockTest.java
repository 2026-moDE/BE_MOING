package com.moing.backend.domain.review.service;

import com.moing.backend.domain.block.repository.BlockRepository;
import com.moing.backend.domain.block.service.BlockService;
import com.moing.backend.domain.comment.repository.CommentRepository;
import com.moing.backend.domain.follow.repository.FollowRepository;
import com.moing.backend.domain.piece.repository.PieceRepository;
import com.moing.backend.domain.place.entity.Place;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.review.dto.ReviewDetailResponse;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 리뷰 상세의 차단 검사 테스트.
 *
 * <p>목록은 차단된 작성자의 리뷰를 쿼리에서 빼지만, 상세는 id로 바로 들어오는 경로라
 * 목록을 거치지 않고 열 수 있다. 그 구멍이 막혔는지만 본다.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:reviewdetailblock;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class ReviewDetailBlockTest {

    @Autowired private ReviewRepository reviewRepository;
    @Autowired private PlaceRepository placeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private FollowRepository followRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private PieceRepository pieceRepository;
    @Autowired private BlockRepository blockRepository;

    private ReviewService reviewService;
    private BlockService blockService;

    private Long viewer;
    private Long author;
    private Long reviewId;

    @BeforeEach
    void setUp() {
        blockService = new BlockService(blockRepository, userRepository, followRepository);

        // 상세 조회가 쓰지 않는 의존성은 null로 둔다 (알림 발송·혼잡도 캐시는 쓰기 경로에서만 쓴다).
        // 인자가 하나라도 늘면 여기서 컴파일이 깨지므로 조용히 어긋나지는 않는다
        reviewService = new ReviewService(
                reviewRepository, null, placeRepository, null, userRepository,
                null, null, followRepository, commentRepository, null,
                null, null, pieceRepository, blockService);

        blockRepository.deleteAll();
        reviewRepository.deleteAll();
        placeRepository.deleteAll();

        viewer = saveUser("조회자").getId();
        author = saveUser("작성자").getId();

        Place place = placeRepository.save(Place.builder()
                .name("성수카페")
                .address("서울 성동구")
                .latitude(new BigDecimal("37.5429700"))
                .longitude(new BigDecimal("127.0566000"))
                .build());

        reviewId = reviewRepository.save(Review.builder()
                .userId(author)
                .placeId(place.getId())
                .congestionLevel(CongestionLevel.RELAXED)
                .comment("조용해요")
                .imageUrl("https://img/original/1.jpg")
                .build()).getId();
    }

    @Test
    @DisplayName("차단 전에는 리뷰 상세가 열린다")
    void 차단_전에는_열린다() {
        ReviewDetailResponse response = reviewService.getReviewDetail(viewer, reviewId);

        assertThat(response.id()).isEqualTo(reviewId);
        assertThat(response.comment()).isEqualTo("조용해요");
    }

    @Test
    @DisplayName("내가 차단한 사용자의 리뷰 상세는 USER_ACCESS_DENIED")
    void 차단하면_상세가_막힌다() {
        blockService.block(viewer, author);

        assertThatThrownBy(() -> reviewService.getReviewDetail(viewer, reviewId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("나를 차단한 사용자의 리뷰 상세도 막힌다 (양방향)")
    void 나를_차단해도_상세가_막힌다() {
        blockService.block(author, viewer);

        assertThatThrownBy(() -> reviewService.getReviewDetail(viewer, reviewId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("차단을 해제하면 리뷰 상세가 다시 열린다")
    void 해제하면_다시_열린다() {
        blockService.block(viewer, author);
        blockService.unblock(viewer, author);

        assertThat(reviewService.getReviewDetail(viewer, reviewId).id()).isEqualTo(reviewId);
    }

    @Test
    @DisplayName("제3자를 차단해도 이 리뷰 상세는 열린다")
    void 제3자_차단은_영향이_없다() {
        Long third = saveUser("제3자").getId();
        blockService.block(viewer, third);

        assertThat(reviewService.getReviewDetail(viewer, reviewId).id()).isEqualTo(reviewId);
    }

    @Test
    @DisplayName("내 리뷰는 내가 차단 목록에 올릴 수 없으니 그대로 열린다")
    void 내_리뷰는_열린다() {
        assertThat(reviewService.getReviewDetail(author, reviewId).isMine()).isTrue();
    }

    private User saveUser(String nickname) {
        return userRepository.save(User.builder()
                .socialProvider(SocialProvider.KAKAO)
                .socialId("social-" + nickname)
                .nickname(nickname)
                .build());
    }
}
