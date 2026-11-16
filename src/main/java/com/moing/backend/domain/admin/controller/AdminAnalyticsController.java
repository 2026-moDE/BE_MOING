package com.moing.backend.domain.admin.controller;

import com.moing.backend.domain.admin.dto.AdminCoverageResponse;
import com.moing.backend.domain.admin.dto.AdminFreshnessResponse;
import com.moing.backend.domain.admin.dto.AdminParticipationResponse;
import com.moing.backend.domain.admin.dto.AdminPatternsResponse;
import com.moing.backend.domain.admin.dto.AdminTrustResponse;
import com.moing.backend.domain.admin.service.AdminAnalyticsService;
import com.moing.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 지표 API.
 *
 * <p>"오래되고 검증되지 않은 장소 정보"라는 문제에 현장 인증·72시간 노출·AI 검열·공공데이터
 * 보완으로 답했고 그 결과가 이만큼이라는 것을, 화면이 아니라 숫자로 보여주기 위한 API다.
 *
 * <p>모든 비율에는 분모와 분자를 함께 담는다. 실사용자가 적은 시기에 비율만 내려주면
 * 숫자가 실제보다 크게 읽힌다.
 */
@Tag(name = "Admin Analytics", description = "관리자 서비스 지표 API")
@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    @Operation(summary = "참여 지표",
            description = "현장에서만 쓸 수 있다는 제약을 두고도 실제로 기록하는지를 봅니다. "
                    + "리뷰를 쓴 사람 비율(activation_rate)과 2회 이상 쓴 사람 비율(repeat_rate), "
                    + "주별 활동 사용자(리뷰·댓글 중 하나 이상)를 함께 내려줍니다. "
                    + "내부 계정과 탈퇴 회원은 모든 집계에서 제외합니다.")
    @GetMapping("/participation")
    public ResponseEntity<ApiResponse<AdminParticipationResponse>> getParticipation() {
        return ResponseEntity.ok(ApiResponse.success("success", adminAnalyticsService.getParticipation()));
    }

    @Operation(summary = "최신성 지표",
            description = "지금 지도에 보이는 정보가 얼마나 최신인지를 봅니다. "
                    + "노출 중인 리뷰 수와 작성 후 경과 시간(중앙값·평균), "
                    + "최근 30일 각 날짜 21시(KST) 기준으로 노출 중이던 리뷰 수를 내려줍니다. "
                    + "노출 중인 리뷰가 0건이면 경과 시간은 null입니다.")
    @GetMapping("/freshness")
    public ResponseEntity<ApiResponse<AdminFreshnessResponse>> getFreshness() {
        return ResponseEntity.ok(ApiResponse.success("success", adminAnalyticsService.getFreshness()));
    }

    @Operation(summary = "신뢰 지표",
            description = "광고·허위 정보를 걸러낸 결과를 봅니다. "
                    + "AI 검열이 검사한 댓글 수와 의심 판정 수·사유 분류, "
                    + "사용자 신고로 블라인드된 리뷰 수를 내려줍니다.")
    @GetMapping("/trust")
    public ResponseEntity<ApiResponse<AdminTrustResponse>> getTrust() {
        return ResponseEntity.ok(ApiResponse.success("success", adminAnalyticsService.getTrust()));
    }

    @Operation(summary = "커버리지 지표",
            description = "사용자 기록이 닿은 범위와 공공데이터로 메운 범위를 봅니다. "
                    + "리뷰가 있는 장소 수, 서울시 실시간 도시데이터 지점 수, "
                    + "권역·시군구·장소 분류별 분포를 내려줍니다. "
                    + "권역과 시군구는 장소 주소의 첫 단어와 두 번째 단어로 가릅니다.")
    @GetMapping("/coverage")
    public ResponseEntity<ApiResponse<AdminCoverageResponse>> getCoverage() {
        return ResponseEntity.ok(ApiResponse.success("success", adminAnalyticsService.getCoverage()));
    }

    @Operation(summary = "공유 패턴 지표",
            description = "혼잡 정보가 언제, 어떤 상태로 올라오는지를 봅니다. "
                    + "KST 요일(1=월~7=일)·시각별 리뷰 수를 기록이 없는 칸까지 168칸 모두 내려주고, "
                    + "혼잡도 단계별 분포를 함께 담습니다.")
    @GetMapping("/patterns")
    public ResponseEntity<ApiResponse<AdminPatternsResponse>> getPatterns() {
        return ResponseEntity.ok(ApiResponse.success("success", adminAnalyticsService.getPatterns()));
    }
}
