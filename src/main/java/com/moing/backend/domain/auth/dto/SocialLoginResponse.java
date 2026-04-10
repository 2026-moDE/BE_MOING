package com.moing.backend.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.moing.backend.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

/**
 * 소셜 로그인 응답 DTO
 * 발급된 액세스/리프레시 토큰, 신규 사용자 여부, 사용자 정보를 담는다.
 */
@Getter
@Builder
@JsonPropertyOrder({"access_token", "refresh_token", "is_new_user", "user"})
public class SocialLoginResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("is_new_user")
    private boolean newUser;

    private UserInfo user;

    /**
     * 응답에 포함되는 사용자 기본 정보 DTO
     */
    @Getter
    @Builder
    @JsonPropertyOrder({"id", "nickname", "profile_image_url"})
    public static class UserInfo {
        private Long id;
        private String nickname;
        @JsonProperty("profile_image_url")
        private String profileImageUrl;

        // User 엔티티를 UserInfo DTO로 변환한다.
        public static UserInfo from(User user) {
            return UserInfo.builder()
                    .id(user.getId())
                    .nickname(user.getNickname())
                    .profileImageUrl(user.getProfileImageUrl())
                    .build();
        }
    }
}
