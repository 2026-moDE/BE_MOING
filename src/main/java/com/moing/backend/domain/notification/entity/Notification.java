package com.moing.backend.domain.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "place_id")
    private Long placeId;

    // 리뷰로 이동하는 알림(REACTION, COMMENT 등)에만 채워진다
    @Column(name = "review_id")
    private Long reviewId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", length = 500)
    private String body;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Notification(Long userId, Long placeId, Long reviewId, NotificationType type, String title, String body) {
        this.userId = userId;
        this.placeId = placeId;
        this.reviewId = reviewId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.isRead = false;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
