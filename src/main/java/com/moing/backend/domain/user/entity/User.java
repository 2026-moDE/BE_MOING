package com.moing.backend.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 사용자 엔티티
 * 소셜 로그인 정보, 약관 동의 여부, FCM 토큰 등 사용자 정보를 관리한다.
 * 삭제 시 deleted_at을 설정하는 소프트 딜리트 방식을 사용한다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(columnNames = {"social_provider", "social_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "social_provider", nullable = false, length = 20)
    private String socialProvider;

    @Column(name = "social_id", nullable = false, length = 100)
    private String socialId;

    @Column(length = 20, unique = true, nullable = false)
    private String nickname;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(length = 100)
    private String email;

    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

    @Column(name = "terms_agreed", nullable = false)
    private boolean termsAgreed = false;

    @Column(name = "location_terms_agreed", nullable = false)
    private boolean locationTermsAgreed = false;

    @Column(name = "privacy_agreed", nullable = false)
    private boolean privacyAgreed = false;

    @Column(name = "marketing_agreed", nullable = false)
    private boolean marketingAgreed = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public User(String socialProvider, String socialId, String nickname,
                String profileImageUrl, String email, String fcmToken) {
        this.socialProvider = socialProvider;
        this.socialId = socialId;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.email = email;
        this.fcmToken = fcmToken;
    }

    // FCM 토큰을 업데이트, null을 전달하면 토큰이 제거됨.
    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public void updateOnboardingInfo(String nickname, boolean terms, boolean location, boolean privacy, boolean marketing) {
        this.nickname = nickname;
        this.termsAgreed = terms;
        this.locationTermsAgreed = location;
        this.privacyAgreed = privacy;
        this.marketingAgreed = marketing;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updateProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    // 탈퇴 후 재가입 시 계정 복구 (deleted_at 초기화 및 약관 동의 초기화)
    public void restore(String fcmToken) {
        this.deletedAt = null;
        this.fcmToken = fcmToken;
        this.termsAgreed = false;
        this.locationTermsAgreed = false;
        this.privacyAgreed = false;
        this.marketingAgreed = false;
    }
}
