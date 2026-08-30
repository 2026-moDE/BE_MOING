package com.moing.backend.domain.user.repository;

import com.moing.backend.domain.user.entity.SocialProvider;
import com.moing.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * 사용자 레포지토리
 * 소셜 로그인 제공자와 소셜 ID로 사용자를 조회하는 기능을 제공한다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    // 기존 소셜 로그인 조회 (탈퇴 유저 제외 - @SQLRestriction 적용)
    Optional<User> findBySocialProviderAndSocialId(SocialProvider socialProvider, String socialId);

    // 탈퇴 유저 포함 조회 (재가입 감지용 - native query로 @SQLRestriction 우회)
    // native query는 enum 매핑을 타지 않으므로 SocialProvider.name() 문자열을 넘겨야 한다.
    @Query(value = "SELECT * FROM users WHERE social_provider = :provider AND social_id = :socialId LIMIT 1", nativeQuery = true)
    Optional<User> findBySocialProviderAndSocialIdIncludeDeleted(@Param("provider") String provider, @Param("socialId") String socialId);

    // 닉네임 중복 체크 (탈퇴하지 않은 유저 중 검색)
    boolean existsByNicknameAndDeletedAtIsNull(String nickname);

    // 활성 유저 수
    long countByDeletedAtIsNull();

    // 닉네임 검색 (부분 일치)
    List<User> findByNicknameContaining(String nickname, Pageable pageable);

    // 닉네임 초성 검색 (PostgreSQL 정규식)
    @Query(value = "SELECT * FROM users WHERE deleted_at IS NULL AND nickname ~ :pattern", nativeQuery = true)
    List<User> findByNicknameRegex(@Param("pattern") String pattern, Pageable pageable);

    // 관리자 사용자 목록 (review_count 포함, N+1 방지)
    @Query(value = """
            SELECT u.id, u.nickname, u.email, u.profile_image_url,
                   COUNT(r.id) AS review_count, u.created_at
            FROM users u
            LEFT JOIN reviews r ON r.user_id = u.id
            WHERE u.deleted_at IS NULL
              AND (:cursor IS NULL OR u.id < :cursor)
            GROUP BY u.id
            ORDER BY u.id DESC
            """, nativeQuery = true)
    List<Object[]> findUsersWithReviewCount(@Param("cursor") Long cursor, Pageable pageable);
}
