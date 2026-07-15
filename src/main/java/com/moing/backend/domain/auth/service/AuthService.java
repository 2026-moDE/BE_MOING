package com.moing.backend.domain.auth.service;

import com.moing.backend.domain.auth.dto.*;
import com.moing.backend.domain.user.entity.User;
import com.moing.backend.domain.user.repository.UserRepository;
import com.moing.backend.global.config.JwtTokenProvider;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증(Auth) 서비스
 * 소셜 로그인, 토큰 재발급, 약관 동의, 로그아웃, 회원탈퇴 비즈니스 로직을 처리한다.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final KakaoClient kakaoClient;

    // 소셜 로그인 처리 - 신규 사용자는 DB에 저장하고, 기존 사용자는 FCM 토큰을 갱신
    @Transactional
    public SocialLoginResponse socialLogin(SocialLoginRequest request) {
        String provider = request.getProvider().toLowerCase();

        KakaoClient.KakaoUserInfo userInfo = switch (provider) {
            case "kakao" -> kakaoClient.getUserInfo(request.getAccessToken());
            default -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };

        boolean[] isNew = {false};
        User user = userRepository.findBySocialProviderAndSocialId(provider, userInfo.socialId())
                .orElseGet(() -> {
                    isNew[0] = true;
                    return userRepository.save(User.builder()
                            .socialProvider(provider)
                            .socialId(userInfo.socialId())
                            .nickname(userInfo.nickname())
                            .profileImageUrl(userInfo.profileImageUrl())
                            .fcmToken(request.getFcmToken())
                            .build());
                });

        if (!isNew[0] && request.getFcmToken() != null) {
            user.updateFcmToken(request.getFcmToken());
        }

        return SocialLoginResponse.builder()
                .accessToken(jwtTokenProvider.generateAccessToken(user.getId()))
                .refreshToken(jwtTokenProvider.generateRefreshToken(user.getId()))
                .newUser(isNew[0])
                .user(SocialLoginResponse.UserInfo.from(user))
                .build();
    }

    // 로그아웃 처리 - FCM 토큰을 null로 설정해 푸시 알림을 차단
    @Transactional
    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        user.updateFcmToken(null);
    }

    // 회원탈퇴 처리 - @SQLDelete에 의해 deleted_at이 현재 시간으로 설정(소프트 딜리트)
    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        // 닉네임 뒤에 타임스탬프를 붙여 기존 닉네임 선점을 해제 -> 다른 사용자가 탈퇴한 사람 닉네임 사용 가능하도록
        user.updateNickname(user.getNickname() + "_deleted_" + System.currentTimeMillis());
        userRepository.delete(user);
    }

    // 토큰 재발급 - 리프레시 토큰이 유효하면 새 액세스 토큰을 발급
    public TokenRefreshResponse refresh(TokenRefreshRequest request) {
        if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        Long userId = jwtTokenProvider.getUserIdFromToken(request.getRefreshToken());
        return new TokenRefreshResponse(jwtTokenProvider.generateAccessToken(userId));
    }

    // 온보딩 - 닉네임 중복 체크와 유저 정보 업데이트 처리
    @Transactional
    public void onboard(Long userId, OnboardingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        // 1. 이미 온보딩을 완료한 유저인지 체크 (선택)
        if (user.isTermsAgreed()) {
            throw new CustomException(ErrorCode.ALREADY_ONBOARDED); // 409 에러
        }

        // 2. 닉네임 중복 체크 (탈퇴하지 않은 유저 중 검색)
        if (userRepository.existsByNicknameAndDeletedAtIsNull(request.nickname())) {
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME); // 409 에러
        }

        // 3. 정보 업데이트
        user.updateOnboardingInfo(
                request.nickname(),
                request.termsAgreed(),
                request.locationTermsAgreed(),
                request.privacyAgreed(),
                request.marketingAgreed()
        );

        if (request.profileImageUrl() != null) {
            user.updateProfileImageUrl(request.profileImageUrl());
        }
    }
}
