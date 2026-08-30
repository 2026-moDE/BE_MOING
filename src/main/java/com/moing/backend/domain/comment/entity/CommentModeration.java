package com.moing.backend.domain.comment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 댓글 AI 검열 결과.
 *
 * <p>댓글 작성 흐름과는 완전히 분리되어 있다. 스케줄러가 아직 검사하지 않은 댓글을 모아
 * AI에 물어본 뒤 결과만 여기 쌓는다. 유저에게 노출되는 값은 없고, 관리자가 확인하는 용도다.
 *
 * <p>reason과 model을 남기는 이유는 프롬프트를 고쳐가며 검증하기 위해서다.
 * 어떤 모델의 어떤 판단이 틀렸는지 남아 있어야 오탐을 줄일 수 있다.
 */
@Entity
@Table(name = "comment_moderations",
        uniqueConstraints = @UniqueConstraint(name = "uk_comment_moderations_comment_id", columnNames = "comment_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentModeration {

    public static final String VERDICT_SAFE = "SAFE";
    public static final String VERDICT_SUSPECT = "SUSPECT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    // SAFE / SUSPECT
    @Column(name = "verdict", nullable = false, length = 20)
    private String verdict;

    // 욕설 / 스팸 / 광고 / 혐오 등. SAFE면 null
    @Column(name = "category", length = 30)
    private String category;

    // AI가 밝힌 근거. 오탐 분석용
    @Column(name = "reason", length = 300)
    private String reason;

    // 어떤 모델의 판단인지. 프롬프트/모델을 바꿔가며 비교하려면 필요하다
    @Column(name = "model", nullable = false, length = 50)
    private String model;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @Builder
    public CommentModeration(Long commentId, String verdict, String category, String reason, String model) {
        this.commentId = commentId;
        this.verdict = verdict;
        this.category = category;
        this.reason = reason;
        this.model = model;
        this.checkedAt = LocalDateTime.now();
    }

    public boolean isSuspect() {
        return VERDICT_SUSPECT.equals(verdict);
    }
}
