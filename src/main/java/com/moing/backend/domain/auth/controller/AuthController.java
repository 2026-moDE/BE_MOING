package com.moing.backend.domain.auth.controller;

import com.moing.backend.domain.auth.dto.SocialLoginRequest;
import com.moing.backend.domain.auth.dto.SocialLoginResponse;
import com.moing.backend.domain.auth.dto.TermsRequest;
import com.moing.backend.domain.auth.dto.TokenRefreshRequest;
import com.moing.backend.domain.auth.dto.TokenRefreshResponse;
import com.moing.backend.domain.auth.service.AuthService;
import com.moing.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth 컨트롤러
 * 소셜 로그인, 토큰 재발급, 약관 동의, 로그아웃, 회원탈퇴 API를 제공한다.
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

    // 약관 동의
    @PostMapping("/terms")
    public ResponseEntity<ApiResponse<Void>> terms(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid TermsRequest request) {
        authService.terms(userId, request);
        return ResponseEntity.ok(ApiResponse.success("약관 동의 완료", null));
    }
}
