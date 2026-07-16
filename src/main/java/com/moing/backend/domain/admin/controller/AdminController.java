package com.moing.backend.domain.admin.controller;

import com.moing.backend.domain.admin.dto.AdminLoginRequest;
import com.moing.backend.domain.admin.dto.AdminLoginResponse;
import com.moing.backend.domain.admin.dto.AdminReportListResponse;
import com.moing.backend.domain.admin.dto.AdminReportProcessRequest;
import com.moing.backend.domain.admin.dto.AdminStatsResponse;
import com.moing.backend.domain.admin.service.AdminAuthService;
import com.moing.backend.domain.admin.service.AdminReportService;
import com.moing.backend.domain.admin.service.AdminStatsService;
import com.moing.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin", description = "관리자 API")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminAuthService adminAuthService;
    private final AdminStatsService adminStatsService;
    private final AdminReportService adminReportService;

    @Operation(summary = "관리자 로그인", description = "이메일과 비밀번호로 관리자 로그인 후 JWT 토큰을 발급합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호가 올바르지 않습니다")
    })
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AdminLoginResponse>> login(
            @RequestBody @Valid AdminLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("로그인 성공", adminAuthService.login(request)));
    }

    @Operation(summary = "대시보드 통계", description = "관리자 대시보드에 표시할 주요 통계를 조회합니다.")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AdminStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.success("success", adminStatsService.getStats()));
    }

    @Operation(summary = "신고 목록 조회", description = "신고 목록을 커서 기반 페이지네이션으로 조회합니다. status 필터 선택 가능.")
    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<AdminReportListResponse>> getReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        return ResponseEntity.ok(ApiResponse.success("success",
                adminReportService.getReports(status, cursor, limit)));
    }

    @Operation(summary = "신고 처리", description = "신고를 RESOLVED(블라인드) 또는 REJECTED(반려) 처리합니다.")
    @PatchMapping("/reports/{id}")
    public ResponseEntity<ApiResponse<Void>> processReport(
            @PathVariable Long id,
            @RequestBody @Valid AdminReportProcessRequest request,
            @AuthenticationPrincipal Long adminId) {
        adminReportService.processReport(id, request.getStatus(), adminId);
        return ResponseEntity.ok(ApiResponse.success("처리 완료", null));
    }
}
