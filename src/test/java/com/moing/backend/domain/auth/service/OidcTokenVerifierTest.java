package com.moing.backend.domain.auth.service;

import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ID token 검증 로직 테스트
 * 실제 구글/애플 대신 테스트용 RSA 키로 토큰을 만들어, 위조·만료·대상 불일치가 모두 거부되는지 확인한다.
 */
class OidcTokenVerifierTest {

    private static final String JWKS_URL = "https://example.test/keys";
    private static final String ISSUER = "https://accounts.google.com";
    private static final List<String> AUDIENCES = List.of("ios-client-id", "android-client-id");

    private static KeyPair validKeyPair;
    private static KeyPair attackerKeyPair;

    private final OidcTokenVerifier verifier = new OidcTokenVerifier(stubJwksProvider(() -> validKeyPair.getPublic()));

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        validKeyPair = generator.generateKeyPair();
        attackerKeyPair = generator.generateKeyPair();
    }

    @Test
    @DisplayName("정상 토큰은 검증을 통과하고 클레임을 돌려준다")
    void acceptsValidToken() {
        String token = token(validKeyPair.getPrivate(), ISSUER, "ios-client-id", plusMinutes(10));

        Claims claims = verifier.verify(token, JWKS_URL, ISSUER, AUDIENCES);

        assertThat(claims.getSubject()).isEqualTo("social-user-1");
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("등록된 client id 중 아무거나 맞으면 통과한다 (iOS/안드로이드 분리 대응)")
    void acceptsAnyRegisteredAudience() {
        String token = token(validKeyPair.getPrivate(), ISSUER, "android-client-id", plusMinutes(10));

        assertThat(verifier.verify(token, JWKS_URL, ISSUER, AUDIENCES).getSubject()).isEqualTo("social-user-1");
    }

    @Test
    @DisplayName("다른 키로 서명한 위조 토큰은 거부된다")
    void rejectsForgedSignature() {
        String forged = token(attackerKeyPair.getPrivate(), ISSUER, "ios-client-id", plusMinutes(10));

        assertUnauthorized(forged);
    }

    @Test
    @DisplayName("만료된 토큰은 거부된다")
    void rejectsExpiredToken() {
        String expired = token(validKeyPair.getPrivate(), ISSUER, "ios-client-id", plusMinutes(-1));

        assertUnauthorized(expired);
    }

    @Test
    @DisplayName("발급자가 다르면 거부된다")
    void rejectsWrongIssuer() {
        String wrongIssuer = token(validKeyPair.getPrivate(), "https://evil.test", "ios-client-id", plusMinutes(10));

        assertUnauthorized(wrongIssuer);
    }

    @Test
    @DisplayName("다른 앱을 위해 발급된 토큰(aud 불일치)은 거부된다")
    void rejectsWrongAudience() {
        String otherApp = token(validKeyPair.getPrivate(), ISSUER, "someone-elses-client-id", plusMinutes(10));

        assertUnauthorized(otherApp);
    }

    @Test
    @DisplayName("client id 설정이 비어 있으면 통과시키지 않고 서버 에러로 막는다")
    void rejectsWhenAudiencesNotConfigured() {
        String token = token(validKeyPair.getPrivate(), ISSUER, "ios-client-id", plusMinutes(10));

        assertThatThrownBy(() -> verifier.verify(token, JWKS_URL, ISSUER, List.of()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("서명 없는(alg=none) 토큰은 거부된다")
    void rejectsUnsignedToken() {
        String unsigned = Jwts.builder()
                .setIssuer(ISSUER)
                .setAudience("ios-client-id")
                .setSubject("social-user-1")
                .setExpiration(plusMinutes(10))
                .compact();

        assertUnauthorized(unsigned);
    }

    private void assertUnauthorized(String token) {
        assertThatThrownBy(() -> verifier.verify(token, JWKS_URL, ISSUER, AUDIENCES))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    private String token(PrivateKey signingKey, String issuer, String audience, Date expiration) {
        return Jwts.builder()
                .setHeaderParam("kid", "test-kid")
                .setIssuer(issuer)
                .setAudience(audience)
                .setSubject("social-user-1")
                .claim("email", "user@example.com")
                .setIssuedAt(new Date())
                .setExpiration(expiration)
                .signWith(signingKey, SignatureAlgorithm.RS256)
                .compact();
    }

    private static Date plusMinutes(int minutes) {
        return new Date(System.currentTimeMillis() + minutes * 60_000L);
    }

    // JWKS를 실제로 받아오지 않고 테스트 키를 돌려주는 스텁
    private static JwksProvider stubJwksProvider(java.util.function.Supplier<PublicKey> keySupplier) {
        return new JwksProvider(null) {
            @Override
            public PublicKey getPublicKey(String jwksUrl, String kid) {
                return keySupplier.get();
            }
        };
    }
}
