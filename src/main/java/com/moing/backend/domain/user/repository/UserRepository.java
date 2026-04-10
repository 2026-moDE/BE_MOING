package com.moing.backend.domain.user.repository;

import com.moing.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 사용자 레포지토리
 * 소셜 로그인 제공자와 소셜 ID로 사용자를 조회하는 기능을 제공한다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    // 소셜 로그인 제공자와 소셜 ID로 사용자 조회
    Optional<User> findBySocialProviderAndSocialId(String socialProvider, String socialId);
}
