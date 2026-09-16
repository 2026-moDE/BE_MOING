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
// 탈퇴 시 닉네임도 함께 해제해 다른 사용자가 쓸 수 있게 한다.
// 엔티티에서 닉네임을 바꿔두는 방식은 동작하지 않는다 - Hibernate가 삭제 예정 엔티티의
// 더티 업데이트를 건너뛰어 조용히 버려진다. 그래서 소프트 딜리트 문에 함께 넣는다.
// id는 유일하고 짧아서 unique 제약과 varchar(20) 둘 다 안전하다.
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW(), nickname = 'deleted_' || id WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_provider", nullable = false, length = 20)
    private SocialProvider socialProvider;

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

    // 알림함에 마지막으로 들어온 시각. 이 시각 이후에 도착한 알림이 있으면
    // 알림 아이콘에 점을 띄운다. 개별 읽음(is_read)과는 별개다.
    // 한 번도 안 들어온 유저는 null이고, 알림이 하나라도 있으면 점이 켜진다
    @Column(name = "notifications_checked_at")
    private LocalDateTime notificationsCheckedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public User(SocialProvider socialProvider, String socialId, String nickname,
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

    // 리사이즈된 프로필 이미지 URL (Lambda가 profile_original/ → profile/에 생성)
    // 리사이즈 대상이 아닌 옛 이미지는 원본으로 폴백
    public String getProfileUrl() {
        if (profileImageUrl == null || !profileImageUrl.contains("/profile_original/")) {
            return profileImageUrl;
        }
        return profileImageUrl.replace("/profile_original/", "/profile/");
    }

    // 알림함 방문 시각을 지금으로 갱신 (알림 아이콘의 점을 끈다)
    public void markNotificationsChecked() {
        this.notificationsCheckedAt = LocalDateTime.now();
    }

    // 탈퇴 후 재가입 시 계정 복구 (deleted_at 초기화 및 약관 동의 초기화)
    public void restore(String fcmToken) {
        this.deletedAt = null;
        this.fcmToken = fcmToken;
        // 탈퇴는 소프트 딜리트라 옛 알림이 그대로 남아 있다. 재가입 시점을 방문 시각으로
        // 찍어, 탈퇴 전 알림 때문에 점이 켜진 상태로 시작하지 않게 한다
        this.notificationsCheckedAt = LocalDateTime.now();
        this.termsAgreed = false;
        this.locationTermsAgreed = false;
        this.privacyAgreed = false;
        this.marketingAgreed = false;
    }
}
