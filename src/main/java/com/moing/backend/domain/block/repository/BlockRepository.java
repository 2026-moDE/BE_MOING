package com.moing.backend.domain.block.repository;

import com.moing.backend.domain.block.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BlockRepository extends JpaRepository<Block, Long> {

    Optional<Block> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    /**
     * 내가 차단한 목록 (최근 차단순).
     *
     * <p>{@code created_at}이 같은 초에 들어온 행끼리 순서가 흔들리지 않도록 id를 뒤에 붙인다.
     */
    List<Block> findByBlockerIdOrderByCreatedAtDescIdDesc(Long blockerId);

    /**
     * 두 사용자 사이에 차단이 있는지 (방향 무관).
     *
     * <p>쓰기 경로(댓글·반응·친구 요청)가 쓴다. 내가 차단했든 상대가 날 차단했든
     * 똑같이 막아야 하므로 한 번의 조회로 양쪽을 본다.
     */
    @Query("""
            SELECT COUNT(b) > 0 FROM Block b
            WHERE (b.blockerId = :userId AND b.blockedId = :otherUserId)
               OR (b.blockerId = :otherUserId AND b.blockedId = :userId)
            """)
    boolean existsBetween(@Param("userId") Long userId, @Param("otherUserId") Long otherUserId);

    /**
     * 조회자와 차단 관계에 있는 사용자 id 전체 (내가 차단한 사람 + 나를 차단한 사람).
     *
     * <p>쿼리에 NOT EXISTS를 끼울 수 없는 조회 경로(이미 메모리에서 거르는 매거진,
     * 친구 검색 결과)가 쓴다. 차단은 사용자당 많아질 종류의 데이터가 아니라 전부 읽어도 된다.
     */
    @Query("""
            SELECT CASE WHEN b.blockerId = :userId THEN b.blockedId ELSE b.blockerId END
            FROM Block b
            WHERE b.blockerId = :userId OR b.blockedId = :userId
            """)
    List<Long> findRelatedUserIds(@Param("userId") Long userId);
}
