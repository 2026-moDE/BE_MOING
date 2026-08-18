package com.moing.backend.domain.auth.service;

import com.moing.backend.domain.auth.dto.SocialUserInfo;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 애플 로그인 클라이언트
 * 앱이 Sign in with Apple로 받은 identity token(JWT)을 검증하고 사용자 정보를 꺼낸다.
 *
 * 주의: 애플은 identity token에 이름을 담지 않는다.
 * 이름은 최초 인증 응답에만 별도로 담겨 오고 재로그인 시에는 오지 않으므로, nickname은 항상 null이다.
 * (닉네임은 어차피 온보딩에서 사용자가 직접 정한다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppleClient {

    private final OidcTokenVerifier tokenVerifier;

    @Value("${apple.jwks-url}")
    private String jwksUrl;

    @Value("${apple.issuer}")
    private String issuer;

    @Value("${apple.client-ids}")
    private List<String> clientIds;

    public SocialUserInfo getUserInfo(String identityToken) {
        Claims claims = tokenVerifier.verify(identityToken, jwksUrl, issuer, clientIds);

        return new SocialUserInfo(
                claims.getSubject(),                       // 애플 고유 ID (sub)
                null,                                      // 애플은 이름을 주지 않는다
                null,                                      // 프로필 이미지도 없다
                claims.get("email", String.class)
        );
    }
}
