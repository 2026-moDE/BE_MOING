package com.moing.backend.domain.block.service;

import com.moing.backend.domain.block.dto.BlockListResponse;
import com.moing.backend.domain.block.repository.BlockRepository;
import com.moing.backend.domain.follow.entity.Follow;
import com.moing.backend.domain.follow.entity.FollowStatus;
import com.moing.backend.domain.follow.repository.FollowRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 차단 동작 테스트.
 *
 * <p>차단은 두 테이블을 함께 건드리므로(차단 행 추가 + 친구 관계 삭제) mock 대신 실제
 * 레포지토리로 돌린다 — unique 제약과 삭제 범위가 이 테스트의 대상이다.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:blockservice;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class BlockServiceTest {

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FollowRepository followRepository;

    private BlockService blockService;

    private Long me;
    private Long other;

    @BeforeEach
    void setUp() {
        blockService = new BlockService(blockRepository, userRepository, followRepository);

        blockRepository.deleteAll();
        followRepository.deleteAll();

        me = saveUser("나").getId();
        other = saveUser("상대").getId();
    }

    @Test
    @DisplayName("본인을 차단하면 CANNOT_BLOCK_SELF")
    void 본인은_차단할_수_없다() {
        assertThatThrownBy(() -> blockService.block(me, me))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_BLOCK_SELF);
    }

    @Test
    @DisplayName("없는 사용자를 차단하면 USER_NOT_FOUND")
    void 없는_사용자는_차단할_수_없다() {
        assertThatThrownBy(() -> blockService.block(me, 9999L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("같은 사용자를 두 번 차단해도 행은 하나다")
    void 두_번_차단해도_행은_하나다() {
        blockService.block(me, other);
        blockService.block(me, other);

        assertThat(blockRepository.findByBlockerIdOrderByCreatedAtDescIdDesc(me)).hasSize(1);
    }

    @Test
    @DisplayName("차단하면 친구 관계와 양쪽 친구 요청이 정리된다")
    void 차단하면_친구_관계가_정리된다() {
        // 양방향 ACCEPTED 두 행이 친구 관계다
        saveFollow(me, other, FollowStatus.ACCEPTED);
        saveFollow(other, me, FollowStatus.ACCEPTED);
        // 제3자와의 친구 관계는 건드리지 않아야 한다
        Long third = saveUser("제3자").getId();
        saveFollow(me, third, FollowStatus.ACCEPTED);

        blockService.block(me, other);

        assertThat(followRepository.findByFollowerIdAndFollowingId(me, other)).isEmpty();
        assertThat(followRepository.findByFollowerIdAndFollowingId(other, me)).isEmpty();
        assertThat(followRepository.findByFollowerIdAndFollowingId(me, third)).isPresent();
    }

    @Test
    @DisplayName("차단당한 쪽이 보낸 PENDING 요청도 함께 지워진다")
    void 받은_요청도_정리된다() {
        saveFollow(other, me, FollowStatus.PENDING);

        blockService.block(me, other);

        assertThat(followRepository.findByFollowerIdAndFollowingId(other, me)).isEmpty();
    }

    @Test
    @DisplayName("차단을 해제해도 친구 관계는 복구되지 않는다")
    void 해제해도_친구_관계는_복구되지_않는다() {
        saveFollow(me, other, FollowStatus.ACCEPTED);
        saveFollow(other, me, FollowStatus.ACCEPTED);

        blockService.block(me, other);
        blockService.unblock(me, other);

        assertThat(blockRepository.existsBetween(me, other)).isFalse();
        // 다시 친구가 되려면 요청을 새로 보내야 한다
        assertThat(followRepository.findByFollowerIdAndFollowingId(me, other)).isEmpty();
        assertThat(followRepository.findByFollowerIdAndFollowingId(other, me)).isEmpty();
    }

    @Test
    @DisplayName("차단 상태가 아닌 사용자를 해제해도 성공한다")
    void 해제는_멱등이다() {
        blockService.unblock(me, other);
        blockService.unblock(me, other);

        assertThat(blockRepository.existsBetween(me, other)).isFalse();
    }

    @Test
    @DisplayName("차단 여부는 방향을 가리지 않는다")
    void 차단_여부는_양방향이다() {
        blockService.block(other, me);

        // 내가 만든 행이 아니어도 막혀야 한다
        assertThatThrownBy(() -> blockService.validateNotBlocked(me, other))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_ACCESS_DENIED);

        assertThat(blockService.getRelatedUserIds(me)).containsExactly(other);
        assertThat(blockService.getRelatedUserIds(other)).containsExactly(me);
    }

    @Test
    @DisplayName("차단 목록은 최근 차단순이고 내가 차단한 사람만 담는다")
    void 차단_목록은_최근순이다() {
        Long third = saveUser("제3자").getId();

        blockService.block(me, other);
        blockService.block(me, third);
        // 나를 차단한 사람은 내 목록에 들어가지 않는다
        Long blocker = saveUser("나를차단한사람").getId();
        blockService.block(blocker, me);

        List<BlockListResponse.BlockItem> blocks = blockService.getBlocks(me).blocks();

        assertThat(blocks).extracting(BlockListResponse.BlockItem::userId)
                .containsExactly(third, other);
        assertThat(blocks.get(0).nickname()).isEqualTo("제3자");
        assertThat(blocks.get(0).blockedAt()).isNotNull();
    }

    @Test
    @DisplayName("본인끼리는 차단 검사를 통과한다")
    void 본인은_검사를_통과한다() {
        blockService.validateNotBlocked(me, me);
    }

    private User saveUser(String nickname) {
        return userRepository.save(User.builder()
                .socialProvider(SocialProvider.KAKAO)
                .socialId("social-" + nickname)
                .nickname(nickname)
                .build());
    }

    private void saveFollow(Long followerId, Long followingId, FollowStatus status) {
        followRepository.save(Follow.builder()
                .followerId(followerId)
                .followingId(followingId)
                .status(status)
                .build());
    }
}
