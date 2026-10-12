package com.moing.backend.domain.congestion.controller;

import com.moing.backend.domain.congestion.dto.CongestionResponse;
import com.moing.backend.domain.congestion.dto.SeoulAreaSearchResponse;
import com.moing.backend.domain.congestion.service.CongestionService;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import com.moing.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/congestion")
@RequiredArgsConstructor
public class CongestionController {

    private final CongestionService congestionService;

    // 기준 좌표에서 가장 가까운 지역의 실시간 혼잡도 + 12시간 예측
    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<CongestionResponse>> getNearbyCongestion(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude
    ) {
        if (latitude == null || longitude == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return ResponseEntity.ok(ApiResponse.success("success",
                congestionService.getNearbyCongestion(latitude, longitude)));
    }

    // 지역 검색 (사용자가 혼잡도를 볼 지역을 직접 고르는 화면용)
    // keyword 없으면 선택 가능한 전체 지역을 가나다순으로 내려준다
    @GetMapping("/areas")
    public ResponseEntity<ApiResponse<SeoulAreaSearchResponse>> searchAreas(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String keyword
    ) {
        return ResponseEntity.ok(ApiResponse.success("success",
                congestionService.searchAreas(keyword)));
    }

    // 고른 지역의 실시간 혼잡도 + 12시간 예측 + 날씨
    // /areas 검색 결과의 id를 그대로 넘긴다 (좌표로 최근접을 다시 찾으면 옆 지역이 잡힐 수 있다)
    @GetMapping("/areas/{id}")
    public ResponseEntity<ApiResponse<CongestionResponse>> getAreaCongestion(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success("success",
                congestionService.getAreaCongestion(id)));
    }
}
