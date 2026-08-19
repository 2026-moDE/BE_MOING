package com.moing.backend.domain.user.entity;

import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;

/**
 * 소셜 로그인 제공자
 * KAKAO  - 카카오 (access token으로 사용자 정보 조회)
 * GOOGLE - 구글 (access token으로 사용자 정보 조회)
 * APPLE  - 애플 (identity token(JWT) 검증)
 */
public enum SocialProvider {
    KAKAO,
    GOOGLE,
    APPLE;

    // 클라이언트가 보내는 provider 문자열("kakao", "Kakao" 등)을 Enum으로 변환
    // 지원하지 않는 값이면 400 에러
    public static SocialProvider from(String provider) {
        if (provider == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        try {
            return SocialProvider.valueOf(provider.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }
}
