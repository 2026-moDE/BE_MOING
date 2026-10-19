package com.moing.backend.domain.block.service;

import com.moing.backend.domain.block.dto.BlockListResponse;
import com.moing.backend.domain.block.entity.Block;
import com.moing.backend.domain.block.repository.BlockRepository;
import com.moing.backend.domain.follow.repository.FollowRepository;
import com.moing.backend.domain.user.entity.User;
import com.moing.backend.domain.user.repository.UserRepository;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 사용자 차단.
 *
 * <p>요구사항은 하나다 — 내가 고른 사용자를 내 앱에서 안 보이게 한다. 상호 차단이나
 * 단계별 정책은 두지 않는다. 차단 행은 단방향으로 한 건만 쌓이고, 숨기는 일은
 * 조회 쪽에서 그 행을 양방향으로 읽어 처리한다({@link BlockRepository} 참고).
 */
@Service
@RequiredArgsConstructor
public class BlockService {

    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    /**
     * 차단.
     *
     * <p>이미 차단한 상대면 아무것도 하지 않고 성공으로 끝낸다(멱등). 클라이언트가 재시도하거나
     * 목록이 덜 갱신된 상태에서 또 눌러도 409로 떨어지면 차단 버튼이 고장난 것처럼 보인다.
     *
     * <p>차단과 함께 친구 관계와 양쪽의 친구 요청을 지운다. 친구 목록은 차단 필터를 거치지 않는
     * 화면이 아니라도, 관계가 남아 있으면 "차단했는데 친구"라는 상태가 생긴다. 해제해도
     * 복구하지 않으므로 다시 친구가 되려면 요청을 새로 보내야 한다.
     */
    @Transactional
    public void block(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new CustomException(ErrorCode.CANNOT_BLOCK_SELF);
        }

        userRepository.findById(blockedId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 이미 차단한 상대라도 친구 관계 정리는 다시 시도한다.
        // 차단 행만 남고 친구 관계가 덜 지워진 상태(예전 버전으로 차단한 행)를 바로잡는다
        followRepository.deleteBetween(blockerId, blockedId);

        if (blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId).isPresent()) {
            return;
        }

        try {
            // 동시에 두 번 눌러도 중복 행이 생기지 않도록 unique 제약 위반을 멱등 성공으로 돌린다
            blockRepository.saveAndFlush(Block.builder()
                    .blockerId(blockerId)
                    .blockedId(blockedId)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 먼저 들어간 행이 곧 원하는 상태다
        }
    }

    /**
     * 차단 해제.
     *
     * <p>차단 상태가 아니어도 성공으로 끝낸다(멱등). 친구 관계는 복구하지 않는다 —
     * 차단 시점에 지운 관계를 되살리면 상대가 모르는 사이에 다시 친구가 된다.
     */
    @Transactional
    public void unblock(Long blockerId, Long blockedId) {
        blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .ifPresent(blockRepository::delete);
    }

    /** 내가 차단한 사용자 목록 (최근 차단순) */
    @Transactional(readOnly = true)
    public BlockListResponse getBlocks(Long userId) {
        List<Block> blocks = blockRepository.findByBlockerIdOrderByCreatedAtDescIdDesc(userId);
        if (blocks.isEmpty()) {
            return new BlockListResponse(List.of());
        }

        // 차단 건마다 조회하지 않고 한 번에 읽는다
        Map<Long, User> userMap = userRepository.findAllById(
                        blocks.stream().map(Block::getBlockedId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return new BlockListResponse(blocks.stream()
                // 탈퇴한 사용자는 @SQLRestriction으로 빠지므로 목록에서도 제외한다.
                // 차단 행은 남겨둔다 — 복구 계정이 생길 때 차단이 풀리지 않게
                .filter(b -> userMap.containsKey(b.getBlockedId()))
                .map(b -> {
                    User user = userMap.get(b.getBlockedId());
                    return new BlockListResponse.BlockItem(
                            user.getId(),
                            user.getNickname(),
                            user.getProfileImageUrl(),
                            b.getCreatedAt());
                })
                .toList());
    }

    /**
     * 두 사용자 사이에 차단이 있으면 403으로 막는다 (방향 무관).
     *
     * <p>쓰기 경로(댓글·답글·반응·친구 요청)와 타인 프로필 직접 조회가 쓴다.
     */
    @Transactional(readOnly = true)
    public void validateNotBlocked(Long userId, Long otherUserId) {
        if (userId == null || otherUserId == null || userId.equals(otherUserId)) {
            return;
        }
        if (blockRepository.existsBetween(userId, otherUserId)) {
            throw new CustomException(ErrorCode.USER_ACCESS_DENIED);
        }
    }

    /**
     * 조회자와 차단 관계에 있는 사용자 id 집합.
     *
     * <p>쿼리에서 거르지 않고 메모리에서 추리는 조회 경로가 쓴다.
     */
    @Transactional(readOnly = true)
    public Set<Long> getRelatedUserIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return Set.copyOf(blockRepository.findRelatedUserIds(userId));
    }
}
