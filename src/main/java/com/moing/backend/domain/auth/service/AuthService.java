package com.moing.backend.domain.auth.service;

import com.moing.backend.domain.auth.dto.*;
import com.moing.backend.domain.user.entity.SocialProvider;
import com.moing.backend.domain.user.entity.User;
import com.moing.backend.domain.user.repository.UserRepository;
import com.moing.backend.global.config.JwtTokenProvider;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 인증(Auth) 서비스
 * 소셜 로그인, 토큰 재발급, 약관 동의, 로그아웃, 회원탈퇴 비즈니스 로직을 처리한다.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    // 닉네임 컬럼이 20자라, 충돌 시 붙일 접미사("_1234") 자리를 남겨둔다
    private static final int NICKNAME_BASE_MAX = 14;

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final KakaoClient kakaoClient;
    private final GoogleClient googleClient;
    private final AppleClient appleClient;

    // 소셜 로그인 처리 - 신규 사용자는 DB에 저장하고, 기존 사용자는 FCM 토큰을 갱신
    @Transactional
    public SocialLoginResponse socialLogin(SocialLoginRequest request) {
        SocialProvider provider = SocialProvider.from(request.getProvider());

        // 카카오는 액세스 토큰, 구글/애플은 ID token(JWT)을 받는다. 요청 필드는 access_token으로 공통이다.
        SocialUserInfo userInfo = switch (provider) {
            case KAKAO -> kakaoClient.getUserInfo(request.getAccessToken());
            case GOOGLE -> googleClient.getUserInfo(request.getAccessToken());
            case APPLE -> appleClient.getUserInfo(request.getAccessToken());
        };

        // 탈퇴 유저 포함 전체 조회로 재가입 케이스 감지
        User user;
        boolean isNew = false;
        var existing = userRepository.findBySocialProviderAndSocialIdIncludeDeleted(provider.name(), userInfo.socialId());

        if (existing.isPresent()) {
            user = existing.get();
            if (user.getDeletedAt() != null) {
                // 탈퇴 후 재가입: 계정 복구 후 신규 유저로 처리 (온보딩 다시 진행)
                user.restore(request.getFcmToken());
                isNew = true;
            } else {
                // 기존 활성 유저: FCM 토큰만 갱신
                if (request.getFcmToken() != null) {
                    user.updateFcmToken(request.getFcmToken());
                }
            }
        } else {
            // 완전히 신규 유저
            isNew = true;
            user = userRepository.save(User.builder()
                    .socialProvider(provider)
                    .socialId(userInfo.socialId())
                    .nickname(resolveInitialNickname(userInfo.nickname()))
                    .profileImageUrl(userInfo.profileImageUrl())
                    .email(userInfo.email())
                    .fcmToken(request.getFcmToken())
                    .build());
        }

        return SocialLoginResponse.builder()
                .accessToken(jwtTokenProvider.generateAccessToken(user.getId()))
                .refreshToken(jwtTokenProvider.generateRefreshToken(user.getId()))
                .newUser(isNew)
                .user(SocialLoginResponse.UserInfo.from(user))
                .build();
    }

    /**
     * 신규 가입 시 사용할 임시 닉네임을 정한다.
     *
     * 닉네임은 not null + unique인데 소셜에서 받은 이름을 그대로 쓰면 두 가지가 깨진다.
     * - 애플은 이름을 아예 주지 않아 null이다.
     * - 구글/카카오 이름은 다른 사용자와 겹칠 수 있고, 겹치면 가입이 실패한다.
     * 실제 닉네임은 온보딩에서 사용자가 직접 정하므로 여기서는 충돌만 피하면 된다.
     */
    private String resolveInitialNickname(String candidate) {
        String base = (candidate == null || candidate.isBlank()) ? "user" : candidate.trim();
        if (base.length() > NICKNAME_BASE_MAX) {
            base = base.substring(0, NICKNAME_BASE_MAX);
        }

        if (!userRepository.existsByNicknameAndDeletedAtIsNull(base)) {
            return base;
        }

        for (int i = 0; i < 10; i++) {
            String withSuffix = base + "_" + ThreadLocalRandom.current().nextInt(1000, 10000);
            if (!userRepository.existsByNicknameAndDeletedAtIsNull(withSuffix)) {
                return withSuffix;
            }
        }
        // 10번 모두 겹치는 건 사실상 없지만, 그래도 실패하면 시간 기반으로 확실히 고유하게 만든다
        return base + "_" + System.currentTimeMillis() % 100000;
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
