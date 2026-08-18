package com.moing.backend.domain.auth.service;

import com.moing.backend.domain.auth.dto.SocialUserInfo;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 구글 로그인 클라이언트
 * 앱이 Google Sign-In SDK로 받은 ID token(JWT)을 검증하고 사용자 정보를 꺼낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleClient {

    private final OidcTokenVerifier tokenVerifier;

    @Value("${google.jwks-url}")
    private String jwksUrl;

    @Value("${google.issuer}")
    private String issuer;

    @Value("${google.client-ids}")
    private List<String> clientIds;

    public SocialUserInfo getUserInfo(String idToken) {
        Claims claims = tokenVerifier.verify(idToken, jwksUrl, issuer, clientIds);

        return new SocialUserInfo(
                claims.getSubject(),                       // 구글 고유 ID (sub)
                claims.get("name", String.class),
                claims.get("picture", String.class),
                claims.get("email", String.class)
        );
    }
}
