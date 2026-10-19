package com.moing.backend.domain.block.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 사용자 차단.
 *
 * <p>단방향이다 — {@code blocker_id}가 {@code blocked_id}를 안 보겠다는 의사 표시 한 건이고,
 * 상대 쪽에 대칭 행을 만들지 않는다. 조회 필터는 이 한 행을 양쪽 방향으로 읽어
 * 차단한 쪽과 차단당한 쪽의 콘텐츠를 모두 숨긴다.
 *
 * <p>그래서 방향마다 인덱스가 하나씩 필요하다. "내가 차단한 사람" 방향은 unique 제약이
 * 만드는 {@code (blocker_id, blocked_id)} 인덱스가 그대로 받고, "나를 차단한 사람" 방향은
 * 순서를 뒤집은 {@code (blocked_id, blocker_id)}가 받는다. 둘 다 복합이라 조회 필터의
 * NOT EXISTS가 인덱스만 읽고 끝난다(테이블을 다시 뒤지지 않는다).
 *
 * <p>{@code blocker_id} 단일 인덱스는 차단 목록 조회({@code WHERE blocker_id = ?})용이다.
 * 조회 필터는 unique 제약 인덱스를 쓰므로 이 인덱스에 의존하지 않는다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "blocks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"blocker_id", "blocked_id"}),
        indexes = {
                @Index(name = "idx_blocks_blocker_id", columnList = "blocker_id"),
                @Index(name = "idx_blocks_blocked_id_blocker_id", columnList = "blocked_id, blocker_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 차단한 사람 */
    @Column(name = "blocker_id", nullable = false)
    private Long blockerId;

    /** 차단당한 사람 */
    @Column(name = "blocked_id", nullable = false)
    private Long blockedId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Block(Long blockerId, Long blockedId) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
    }
}
