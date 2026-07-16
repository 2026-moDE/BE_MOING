package com.moing.backend.domain.admin.service;

import com.moing.backend.domain.admin.dto.AdminLoginRequest;
import com.moing.backend.domain.admin.dto.AdminLoginResponse;
import com.moing.backend.domain.admin.entity.Admin;
import com.moing.backend.domain.admin.repository.AdminRepository;
import com.moing.backend.global.config.JwtTokenProvider;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request) {
        Admin admin = adminRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_LOGIN_FAILED));

        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new CustomException(ErrorCode.ADMIN_LOGIN_FAILED);
        }

        admin.updateLastLoginAt();

        String accessToken = jwtTokenProvider.generateAccessToken(admin.getId(), admin.getRole());

        return AdminLoginResponse.builder()
                .accessToken(accessToken)
                .admin(AdminLoginResponse.AdminInfo.from(admin))
                .build();
    }
}
