package com.moing.backend.domain.reaction.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
// 한 사람은 리뷰당 이모지를 하나만 가질 수 있다
@Table(name = "reactions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"review_id", "user_id"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 피부색 수정자나 ZWJ가 붙으면 한 이모지도 여러 문자가 되므로 여유를 둔다.
    // 실제 컬럼은 varchar(32). 길이 검증은 ReactionCreateRequest에서 한다.
    @Column(name = "emoji", nullable = false, length = 32)
    private String emoji;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Reaction(Long reviewId, Long userId, String emoji) {
        this.reviewId = reviewId;
        this.userId = userId;
        this.emoji = emoji;
    }

    // 다른 이모지를 누르면 기존 반응을 교체한다 (리뷰당 하나만 가능)
    public void changeEmoji(String emoji) {
        this.emoji = emoji;
    }
}
