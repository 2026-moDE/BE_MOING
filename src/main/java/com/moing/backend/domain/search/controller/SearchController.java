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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    // 장소 검색
    @GetMapping
    public ResponseEntity<ApiResponse<PlaceSearchResponse>> searchPlaces(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String keyword
    ) {
        if (!StringUtils.hasText(keyword)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return ResponseEntity.ok(ApiResponse.success("success", searchService.searchPlaces(userId, keyword)));
    }

    // 최근 검색어 목록 조회
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<SearchHistoryResponse>> getSearchHistory(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success("success", searchService.getSearchHistory(userId)));
    }

    // 최근 검색어 전체 삭제
    @DeleteMapping("/history")
    public ResponseEntity<ApiResponse<Void>> deleteAllSearchHistory(
            @AuthenticationPrincipal Long userId
    ) {
        searchService.deleteAllSearchHistory(userId);
        return ResponseEntity.ok(ApiResponse.success("success", null));
    }

    // 최근 검색어 개별 삭제
    @DeleteMapping("/history/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSearchHistory(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        searchService.deleteSearchHistory(id, userId);
        return ResponseEntity.ok(ApiResponse.success("success", null));
    }

    // 검색 자동완성
    @GetMapping("/autocomplete")
    public ResponseEntity<ApiResponse<AutocompleteResponse>> autocomplete(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String keyword
    ) {
        if (!StringUtils.hasText(keyword)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return ResponseEntity.ok(ApiResponse.success("success", searchService.autocomplete(keyword)));
    }
}
