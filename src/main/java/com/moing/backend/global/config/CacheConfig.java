package com.moing.backend.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 외부 API 응답 캐시 설정.
 *
 * <p>서울시 실시간 도시데이터는 5분 주기로 갱신되므로 더 자주 호출해도 같은 값만 돌아온다.
 * 인스턴스 로컬 캐시라 서버가 여러 대면 대수만큼 호출이 나가지만,
 * 지역이 120여 곳뿐이라 그 정도는 서울시 호출 한도에 문제가 되지 않는다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SEOUL_CONGESTION = "seoulCongestion";

    @Bean
    public CaffeineCacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(SEOUL_CONGESTION);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(200));   // 지역 121곳 + 여유
        return cacheManager;
    }
}
