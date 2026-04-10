package com.moing.backend.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

/**
 * 토큰 재발급 요청 DTO
 * 유효한 리프레시 토큰을 담는다.
 */
@Getter
public class TokenRefreshRequest {

    @NotBlank
    @JsonProperty("refresh_token")
    private String refreshToken;
}
