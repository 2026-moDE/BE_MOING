package com.moing.backend.domain.auth.service;

import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.List;

/**
 * OIDC ID token 검증 (구글/애플 공통)
 *
 * 카카오는 액세스 토큰으로 유저 정보 API를 호출하면 되지만,
 * 구글/애플은 앱이 건네준 ID token(JWT)을 서버가 직접 검증해야 한다.
 * 검증 항목: 서명(제공자 공개키), 발급자(iss), 대상(aud), 만료(exp).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OidcTokenVerifier {

    private final JwksProvider jwksProvider;

    /**
     * @param allowedAudiences 허용할 client id 목록.
     *                         iOS/안드로이드가 서로 다른 client id를 쓰는 경우가 있어 여러 개를 받는다.
     */
    public Claims verify(String idToken, String jwksUrl, String issuer, List<String> allowedAudiences) {
        if (allowedAudiences == null || allowedAudiences.isEmpty()) {
            // client id 설정이 비어 있으면 aud 검증을 못 하므로, 통과시키지 않고 막는다
            log.error("소셜 로그인 client id가 설정되지 않았습니다: {}", issuer);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKeyResolver(new SigningKeyResolverAdapter() {
                        @Override
                        public Key resolveSigningKey(JwsHeader header, Claims claims) {
                            return jwksProvider.getPublicKey(jwksUrl, header.getKeyId());
                        }
                    })
                    .requireIssuer(issuer)
                    .build()
                    .parseClaimsJws(idToken)   // 서명과 만료(exp)는 여기서 검증된다
                    .getBody();

            // aud는 여러 개를 허용해야 해서 직접 검사한다
            if (!allowedAudiences.contains(claims.getAudience())) {
                log.debug("허용되지 않은 aud: {}", claims.getAudience());
                throw new CustomException(ErrorCode.UNAUTHORIZED);
            }

            return claims;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("ID token 검증 실패 ({}): {}", issuer, e.getMessage());
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
    }
}
