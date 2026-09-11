package com.moing.backend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 애플리케이션 공통 빈 설정
 * 외부 HTTP 통신에 사용되는 RestTemplate을 빈으로 등록한다.
 */
@Configuration
public class AppConfig {

    /**
     * 외부 API 호출용 RestTemplate.
     *
     * <p>카카오 로그인·JWKS·카카오 장소검색·서울시 공공데이터가 함께 쓴다.
     * 모두 요청 스레드에서 도는 짧은 JSON 호출이라, 타임아웃이 없으면
     * 상대 서버가 응답을 안 줄 때 사용자 요청이 그대로 묶인다.
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }

    /**
     * AI 검열 전용 RestTemplate.
     *
     * <p>기본 RestTemplate은 타임아웃이 없어 응답이 오지 않으면 무한정 기다린다.
     * 검열은 스케줄러 스레드에서 도는 부가 기능이라, 늦어지면 포기하고 다음 주기를 기다리는 편이 낫다.
     */
    @Bean
    public RestTemplate moderationRestTemplate(
            @Value("${ai.moderation.timeout-seconds}") int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return new RestTemplate(factory);
    }
}
