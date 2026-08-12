package com.moing.backend.domain.comment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {

    // 답글이 달린 댓글을 삭제할 때 내용 대신 노출하는 문구
    public static final String DELETED_CONTENT = "삭제된 댓글";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 최상위 댓글이면 null, 답글이면 부모 댓글 id
    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "content", nullable = false, length = 200)
    private String content;

    @Column(name = "is_secret", nullable = false)
    private boolean isSecret;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Comment(Long reviewId, Long userId, Long parentId, String content, Boolean isSecret) {
        this.reviewId = reviewId;
        this.userId = userId;
        this.parentId = parentId;
        this.content = content;
        this.isSecret = isSecret != null && isSecret;
        this.isDeleted = false;
    }

    public boolean isReply() {
        return parentId != null;
    }

    // 답글이 달려 있어 실제로 지울 수 없는 댓글은 내용만 치환한다
    public void softDelete() {
        this.isDeleted = true;
        this.content = DELETED_CONTENT;
    }
}
