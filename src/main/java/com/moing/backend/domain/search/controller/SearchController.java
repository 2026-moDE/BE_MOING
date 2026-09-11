package com.moing.backend.domain.search.controller;

import com.moing.backend.domain.search.dto.AutocompleteResponse;
import com.moing.backend.domain.search.dto.PlaceSearchResponse;
import com.moing.backend.domain.search.dto.SearchHistoryResponse;
import com.moing.backend.domain.search.service.SearchService;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import com.moing.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    // 장소 검색
    // keyword 없으면 좌표 기준 주변 장소를 내려준다 (keyword·좌표 둘 다 없으면 400)
    @GetMapping
    public ResponseEntity<ApiResponse<PlaceSearchResponse>> searchPlaces(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Integer radius
    ) {
        return ResponseEntity.ok(ApiResponse.success("success", searchService.searchPlaces(userId, keyword, latitude, longitude, radius)));
    }

    // 검색어 저장 (장소 선택 시 클라이언트에서 호출)
    @PostMapping("/history")
    public ResponseEntity<ApiResponse<Void>> saveSearchHistory(
            @AuthenticationPrincipal Long userId,
            @RequestParam String keyword
    ) {
        searchService.saveSearchHistory(userId, keyword);
        return ResponseEntity.ok(ApiResponse.success("success", null));
    }

    // 최근 검색어 목록 조회
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<SearchHistoryResponse>> getSearchHistory(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success("success", searchService.getSearchHistory(userId)));
    }

    // 최근 검색어 전체 삭제
    @DeleteMapping("/history/all")
    public ResponseEntity<ApiResponse<Void>> deleteAllSearchHistory(
            @AuthenticationPrincipal Long userId
    ) {
        searchService.deleteAllSearchHistory(userId);
        return ResponseEntity.ok(ApiResponse.success("success", null));
    }

    // 최근 검색어 개별 삭제 (같은 키워드 전체 삭제)
    @DeleteMapping("/history")
    public ResponseEntity<ApiResponse<Void>> deleteSearchHistoryByKeyword(
            @AuthenticationPrincipal Long userId,
            @RequestParam String keyword
    ) {
        searchService.deleteSearchHistoryByKeyword(userId, keyword);
        return ResponseEntity.ok(ApiResponse.success("success", null));
    }

    // 검색 자동완성
    // 좌표를 주면 거리순으로 정렬한다 (없으면 카카오 기본값인 전국 정확도순)
    @GetMapping("/autocomplete")
    public ResponseEntity<ApiResponse<AutocompleteResponse>> autocomplete(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude
    ) {
        if (!StringUtils.hasText(keyword)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return ResponseEntity.ok(ApiResponse.success("success",
                searchService.autocomplete(keyword, latitude, longitude)));
    }
}
