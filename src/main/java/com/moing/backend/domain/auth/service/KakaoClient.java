package com.moing.backend.domain.auth.service;

import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 카카오 API 클라이언트
 * 카카오 사용자 정보 API를 호출해 소셜 ID, 닉네임, 프로필 이미지를 가져온다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoClient {

    private final RestTemplate restTemplate;

    @Value("${kakao.user-info-url}")
    private String userInfoUrl;

    // 액세스 토큰으로 카카오 사용자 정보를 조회한다. 실패 시 401 에러를 던진다.
    @SuppressWarnings("unchecked")
    public KakaoUserInfo getUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        log.debug("카카오 API 호출");
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    userInfoUrl, HttpMethod.GET, request, Map.class);
            Map<String, Object> body = response.getBody();

            String socialId = String.valueOf(body.get("id"));
            Map<String, Object> kakaoAccount = (Map<String, Object>) body.get("kakao_account");
            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

            String nickname = (String) profile.get("nickname");
            String profileImageUrl = (String) profile.get("profile_image_url");

            return new KakaoUserInfo(socialId, nickname, profileImageUrl);
        } catch (RestClientException e) {
            log.debug("카카오 API 에러: {}", e.getMessage());
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
    }

    public record KakaoUserInfo(String socialId, String nickname, String profileImageUrl) {}
}
