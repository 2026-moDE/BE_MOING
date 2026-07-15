package com.moing.backend.domain.user.repository;

import com.moing.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 사용자 레포지토리
 * 소셜 로그인 제공자와 소셜 ID로 사용자를 조회하는 기능을 제공한다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    // 기존 소셜 로그인 조회 (탈퇴 유저 제외 - @SQLRestriction 적용)
    Optional<User> findBySocialProviderAndSocialId(String socialProvider, String socialId);

    // 탈퇴 유저 포함 조회 (재가입 감지용 - native query로 @SQLRestriction 우회)
    @Query(value = "SELECT * FROM users WHERE social_provider = :provider AND social_id = :socialId LIMIT 1", nativeQuery = true)
    Optional<User> findBySocialProviderAndSocialIdIncludeDeleted(@Param("provider") String provider, @Param("socialId") String socialId);

    // 닉네임 중복 체크 (탈퇴하지 않은 유저 중 검색)
    boolean existsByNicknameAndDeletedAtIsNull(String nickname);
}
