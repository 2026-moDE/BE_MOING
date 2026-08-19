package com.moing.backend.domain.auth.service;

import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OIDC 공개키(JWKS) 조회 및 캐싱
 *
 * 구글/애플은 ID token을 자신들의 RSA 개인키로 서명하고, 대응하는 공개키를 JWKS 엔드포인트로 공개한다.
 * 매 로그인마다 받아오면 느리므로 캐싱하되, 제공자가 키를 교체(rotation)하면
 * 캐시에 없는 kid가 들어오므로 그때 한 번 다시 받아온다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwksProvider {

    private static final long CACHE_TTL_MS = 60 * 60 * 1000L; // 1시간

    private final RestTemplate restTemplate;
    private final Map<String, CachedKeys> cache = new ConcurrentHashMap<>();

    // kid에 해당하는 공개키를 반환한다. 캐시에 없으면 JWKS를 다시 받아온다.
    public PublicKey getPublicKey(String jwksUrl, String kid) {
        if (kid == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        CachedKeys cached = cache.get(jwksUrl);
        if (cached != null && !cached.isExpired() && cached.keys().containsKey(kid)) {
            return cached.keys().get(kid);
        }

        // 캐시 미스 또는 키 교체 - 새로 받아온다
        Map<String, PublicKey> fresh = fetchKeys(jwksUrl);
        cache.put(jwksUrl, new CachedKeys(fresh, System.currentTimeMillis()));

        PublicKey key = fresh.get(kid);
        if (key == null) {
            log.debug("JWKS에 없는 kid: {} ({})", kid, jwksUrl);
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return key;
    }

    @SuppressWarnings("unchecked")
    private Map<String, PublicKey> fetchKeys(String jwksUrl) {
        try {
            Map<String, Object> body = restTemplate.getForObject(jwksUrl, Map.class);
            List<Map<String, String>> keys = (List<Map<String, String>>) body.get("keys");

            Map<String, PublicKey> result = new HashMap<>();
            for (Map<String, String> jwk : keys) {
                // 서명 검증용 RSA 키만 사용한다
                if (!"RSA".equals(jwk.get("kty"))) {
                    continue;
                }
                result.put(jwk.get("kid"), toPublicKey(jwk.get("n"), jwk.get("e")));
            }
            return result;
        } catch (RestClientException e) {
            log.warn("JWKS 조회 실패: {} - {}", jwksUrl, e.getMessage());
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
    }

    // JWK의 modulus(n), exponent(e)로 RSA 공개키를 만든다. 둘 다 base64url 인코딩된 부호 없는 정수다.
    private PublicKey toPublicKey(String n, String e) {
        try {
            BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
            BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
        } catch (Exception ex) {
            log.warn("JWK 파싱 실패: {}", ex.getMessage());
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
    }

    private record CachedKeys(Map<String, PublicKey> keys, long fetchedAt) {
        boolean isExpired() {
            return System.currentTimeMillis() - fetchedAt > CACHE_TTL_MS;
        }
    }
}
