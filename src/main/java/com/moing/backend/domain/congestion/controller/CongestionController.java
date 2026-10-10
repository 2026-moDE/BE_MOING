package com.moing.backend.domain.congestion.controller;

import com.moing.backend.domain.congestion.dto.CongestionResponse;
import com.moing.backend.domain.congestion.service.CongestionService;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import com.moing.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
        return ResponseEntity.ok(ApiResponse.success(
                congestionService.getNearbyCongestion(latitude, longitude)));
    }
}
