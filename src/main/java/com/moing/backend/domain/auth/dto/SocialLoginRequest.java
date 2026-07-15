package com.moing.backend.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

/**
 * 소셜 로그인 요청 DTO
 * 소셜 로그인 제공자, 액세스 토큰, FCM 토큰을 담는다.
 */
@Getter
public class SocialLoginRequest {

    @NotBlank(message = "provider는 필수입니다")
    private String provider;

    @JsonProperty("access_token")
    @NotBlank(message = "access_token은 필수입니다")
    private String accessToken;

    @JsonProperty("fcm_token")
    private String fcmToken;
}
