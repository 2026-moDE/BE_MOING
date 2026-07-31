package com.moing.backend.domain.follow.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "follows",
        uniqueConstraints = @UniqueConstraint(columnNames = {"follower_id", "following_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "follower_id", nullable = false)
    private Long followerId;

    @Column(name = "following_id", nullable = false)
    private Long followingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FollowStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Follow(Long followerId, Long followingId, FollowStatus status) {
        this.followerId = followerId;
        this.followingId = followingId;
        this.status = status;
    }

    public void accept() {
        this.status = FollowStatus.ACCEPTED;
    }

    public void reject() {
        this.status = FollowStatus.REJECTED;
    }

    public void toPending() {
        this.status = FollowStatus.PENDING;
    }
}
