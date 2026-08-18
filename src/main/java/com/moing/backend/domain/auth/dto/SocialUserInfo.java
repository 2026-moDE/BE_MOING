package com.moing.backend.domain.auth.dto;

/**
 * 소셜 제공자에서 가져온 사용자 정보 (제공자 공통)
 *
 * nickname, profileImageUrl, email은 제공자에 따라 null일 수 있다.
 * 특히 애플은 identity token에 이름을 담지 않으므로 nickname이 항상 null이다.
 */
public record SocialUserInfo(
        String socialId,
        String nickname,
        String profileImageUrl,
        String email
) {
    public static SocialUserInfo of(String socialId, String nickname, String profileImageUrl) {
        return new SocialUserInfo(socialId, nickname, profileImageUrl, null);
    }
}
