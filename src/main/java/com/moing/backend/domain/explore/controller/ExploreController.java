package com.moing.backend.domain.explore.controller;

import com.moing.backend.domain.explore.dto.ExploreReviewResponse;
import com.moing.backend.domain.explore.service.ExploreService;
import com.moing.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/explore")
@RequiredArgsConstructor
public class ExploreController {

    private final ExploreService exploreService;

    /**
     * 기준 장소 반경 내 실시간 리뷰 사진 그리드.
     *
     * <p>장소 검색과 최근 검색어는 /api/search가 담당한다. 여기는 좌표를 받은 뒤의 조회만 한다.
     *
     * <p>필수값·범위 검증은 서비스에서 한다(누락도 ErrorCode.INVALID_INPUT으로 떨어뜨리기 위해
     * required = false로 받는다 — 검색 API와 같은 방식).
     */
    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<ExploreReviewResponse>> getExploreReviews(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) String seed,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(ApiResponse.success("success",
                exploreService.getExploreReviews(userId, latitude, longitude, radius, seed, offset, limit)));
    }
}
