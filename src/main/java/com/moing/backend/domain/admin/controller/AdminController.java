package com.moing.backend.domain.admin.controller;

import com.moing.backend.domain.admin.dto.AdminCommentListResponse;
import com.moing.backend.domain.admin.dto.AdminLoginRequest;
import com.moing.backend.domain.admin.dto.AdminLoginResponse;
import com.moing.backend.domain.admin.dto.AdminReportListResponse;
import com.moing.backend.domain.admin.dto.AdminReportProcessRequest;
import com.moing.backend.domain.admin.dto.AdminReviewBlindRequest;
import com.moing.backend.domain.admin.dto.AdminReviewListResponse;
import com.moing.backend.domain.admin.dto.AdminPlaceListResponse;
import com.moing.backend.domain.admin.dto.AdminPlaceUpdateRequest;
import com.moing.backend.domain.admin.dto.AdminStatsResponse;
import com.moing.backend.domain.admin.dto.AdminUserListResponse;
import com.moing.backend.domain.admin.service.AdminAuthService;
import com.moing.backend.domain.admin.service.AdminCommentService;
import com.moing.backend.domain.admin.service.AdminPlaceService;
import com.moing.backend.domain.admin.service.AdminReportService;
import com.moing.backend.domain.admin.service.AdminReviewService;
import com.moing.backend.domain.admin.service.AdminStatsService;
import com.moing.backend.domain.admin.service.AdminUserService;
import com.moing.backend.domain.congestion.dto.SeoulAreaSyncResponse;
import com.moing.backend.domain.congestion.service.SeoulAreaSyncService;
import com.moing.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final AdminCommentService adminCommentService;
    private final AdminStatsService adminStatsService;
    private final AdminPlaceService adminPlaceService;
    private final AdminReportService adminReportService;
    private final AdminReviewService adminReviewService;
    private final AdminUserService adminUserService;
    private final SeoulAreaSyncService seoulAreaSyncService;

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

    @Operation(summary = "리뷰 목록 조회", description = "리뷰 목록을 커서 기반 페이지네이션으로 조회합니다. status 필터(ACTIVE/ARCHIVED/BLINDED) 선택 가능.")
    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<AdminReviewListResponse>> getReviews(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        return ResponseEntity.ok(ApiResponse.success("success",
                adminReviewService.getReviews(status, cursor, limit)));
    }

    @Operation(summary = "리뷰 블라인드 처리", description = "리뷰를 BLINDED(블라인드) 또는 ACTIVE(블라인드 해제) 처리합니다.")
    @PatchMapping("/reviews/{id}")
    public ResponseEntity<ApiResponse<Void>> blindReview(
            @PathVariable Long id,
            @RequestBody @Valid AdminReviewBlindRequest request) {
        adminReviewService.blindReview(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("처리 완료", null));
    }

    @Operation(summary = "댓글 목록 조회", description = "댓글 목록을 커서 기반 페이지네이션으로 조회합니다. status 필터(ACTIVE/DELETED)와 keyword 내용 검색 선택 가능.")
    @GetMapping("/comments")
    public ResponseEntity<ApiResponse<AdminCommentListResponse>> getComments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        return ResponseEntity.ok(ApiResponse.success("success",
                adminCommentService.getComments(status, keyword, cursor, limit)));
    }

    @Operation(summary = "댓글 내리기", description = "댓글을 삭제 처리합니다. 되돌릴 수 없습니다.")
    @DeleteMapping("/comments/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(@PathVariable Long id) {
        adminCommentService.deleteComment(id);
        return ResponseEntity.ok(ApiResponse.success("처리 완료", null));
    }

    @Operation(summary = "장소 목록 조회", description = "장소 목록을 커서 기반 페이지네이션으로 조회합니다. keyword로 이름/주소 검색 가능.")
    @GetMapping("/places")
    public ResponseEntity<ApiResponse<AdminPlaceListResponse>> getPlaces(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        return ResponseEntity.ok(ApiResponse.success("success",
                adminPlaceService.getPlaces(keyword, cursor, limit)));
    }

    @Operation(summary = "장소 수정", description = "장소의 is_active, category를 수정합니다.")
    @PatchMapping("/places/{id}")
    public ResponseEntity<ApiResponse<Void>> updatePlace(
            @PathVariable Long id,
            @RequestBody AdminPlaceUpdateRequest request) {
        adminPlaceService.updatePlace(id, request);
        return ResponseEntity.ok(ApiResponse.success("처리 완료", null));
    }

    @Operation(summary = "사용자 목록 조회", description = "사용자 목록을 커서 기반 페이지네이션으로 조회합니다.")
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<AdminUserListResponse>> getUsers(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        return ResponseEntity.ok(ApiResponse.success("success",
                adminUserService.getUsers(cursor, limit)));
    }

    @Operation(summary = "서울시 지역 목록 동기화",
            description = "서울시 실시간 도시데이터 지역 목록(약 121곳)을 받아 좌표와 함께 저장합니다. "
                    + "지역 혼잡도 조회가 이 목록에서 최근접 지역을 찾으므로 최초 1회 실행이 필요하고, "
                    + "이후에는 주 1회 스케줄러가 자동으로 맞춥니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "동기화 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "서울시 지역 목록 조회 실패")
    })
    @PostMapping("/seoul-areas/sync")
    public ResponseEntity<ApiResponse<SeoulAreaSyncResponse>> syncSeoulAreas() {
        return ResponseEntity.ok(ApiResponse.success("동기화 완료", seoulAreaSyncService.sync()));
    }
}
