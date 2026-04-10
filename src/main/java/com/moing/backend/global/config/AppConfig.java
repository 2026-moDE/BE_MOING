package com.moing.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 애플리케이션 공통 빈 설정
 * 외부 HTTP 통신에 사용되는 RestTemplate을 빈으로 등록한다.
 */
@Configuration
public class AppConfig {

    // 외부 API 호출용 RestTemplate 빈 등록
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
