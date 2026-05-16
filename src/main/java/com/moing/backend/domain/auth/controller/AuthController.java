package com.moing.backend.domain.auth.controller;

import com.moing.backend.domain.auth.dto.*;
import com.moing.backend.domain.auth.service.AuthService;
import com.moing.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Auth 컨트롤러
 * 소셜 로그인, 토큰 재발급, 온보딩, 로그아웃, 회원탈퇴 API를 제공한다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 소셜 로그인
    @PostMapping("/social-login")
    public ResponseEntity<ApiResponse<SocialLoginResponse>> socialLogin(
            @RequestBody @Valid SocialLoginRequest request) {
        SocialLoginResponse response = authService.socialLogin(request);
        return ResponseEntity.ok(ApiResponse.success("로그인 성공", response));
    }

    // 로그아웃
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        authService.logout(userId);
        return ResponseEntity.ok(ApiResponse.success("로그아웃 성공", null));
    }

    // 회원탈퇴
    @DeleteMapping("/withdraw")
    public ResponseEntity<ApiResponse<Void>> withdraw() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        authService.withdraw(userId);
        return ResponseEntity.ok(ApiResponse.success("회원탈퇴 성공", null));
    }

    // 토큰 재발급
    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
            @RequestBody @Valid TokenRefreshRequest request) {
        TokenRefreshResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success("토큰 재발급 성공", response));
    }

    // 온보딩
    @PatchMapping("/onboarding")
    public ResponseEntity<Void> onboardUser(
            @AuthenticationPrincipal Long userId,
            @RequestBody OnboardingRequest request
    ) {
        authService.onboard(userId, request);
        return ResponseEntity.ok().build();
    }
}
