package com.moing.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 애플리케이션 진입점
 * Spring Security 기본 UserDetailsService 자동 설정을 제외하고 실행한다.
 */
@EnableJpaAuditing
@EnableScheduling
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
public class BackendApplication {

	// 애플리케이션 시작
	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
