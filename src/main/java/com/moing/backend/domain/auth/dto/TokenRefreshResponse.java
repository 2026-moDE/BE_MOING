package com.moing.backend.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 토큰 재발급 응답 DTO
 * 새로 발급된 액세스 토큰을 담는다.
 */
@Getter
@AllArgsConstructor
public class TokenRefreshResponse {

    @JsonProperty("access_token")
    private String accessToken;
}
