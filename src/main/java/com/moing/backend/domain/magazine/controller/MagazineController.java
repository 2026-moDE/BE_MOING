package com.moing.backend.domain.magazine.controller;

import com.moing.backend.domain.magazine.dto.MagazineDetailResponse;
import com.moing.backend.domain.magazine.dto.MagazineListResponse;
import com.moing.backend.domain.magazine.service.MagazineService;
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
@RequestMapping("/api/magazines")
@RequiredArgsConstructor
public class MagazineController {

    private final MagazineService magazineService;

    /**
     * 홈 바텀시트 매거진 목록 (가로 스크롤 카드).
     *
     * <p>좌표가 필요한 이유는 날씨로 노출을 가르는 묶음이 있기 때문이다.
     *
     * <p>필수값 검증은 서비스에서 한다(누락도 ErrorCode.INVALID_INPUT으로 떨어뜨리기 위해
     * required = false로 받는다 — 탐색·검색 API와 같은 방식).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<MagazineListResponse>> getMagazines(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(ApiResponse.success("success",
                magazineService.getMagazines(latitude, longitude, limit)));
    }

    /**
     * 매거진 상세 (풀스크린 뷰어).
     *
     * <p>좌표는 선택이다. 주면 아이템마다 거리를 계산해 담고, 없으면 distance가 null이다.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MagazineDetailResponse>> getMagazineDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude
    ) {
        return ResponseEntity.ok(ApiResponse.success("success",
                magazineService.getMagazineDetail(id, latitude, longitude)));
    }
}
