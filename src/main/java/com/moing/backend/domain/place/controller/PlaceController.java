package com.moing.backend.domain.place.controller;

import com.moing.backend.domain.place.dto.HotPlaceResponse;
import com.moing.backend.domain.place.dto.LocationVerifyResponse;
import com.moing.backend.domain.place.dto.PlaceDetailResponse;
import com.moing.backend.domain.place.dto.PlaceNearbyResponse;
import com.moing.backend.domain.place.dto.RegionResponse;
import com.moing.backend.domain.place.dto.SubscribeResponse;
import com.moing.backend.domain.place.service.PlaceService;
import com.moing.backend.domain.review.dto.ReviewListResponse;
import com.moing.backend.domain.review.service.ReviewService;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import com.moing.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceService placeService;
    private final ReviewService reviewService;

    // 장소 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PlaceDetailResponse>> getPlaceDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(placeService.getPlaceDetail(userId, id)));
    }

    // 현재 리뷰 목록 (72h 이내)
    @GetMapping("/{id}/reviews/current")
    public ResponseEntity<ApiResponse<ReviewListResponse>> getCurrentReviews(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getCurrentReviews(id, userId, cursor, limit)));
    }

    // 과거 리뷰 목록 (72h 경과)
    @GetMapping("/{id}/reviews/archived")
    public ResponseEntity<ApiResponse<ReviewListResponse>> getArchivedReviews(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getArchivedReviews(id, userId, cursor, limit)));
    }

    // 위치 인증 확인
    @GetMapping("/{id}/verify-location")
    public ResponseEntity<ApiResponse<LocationVerifyResponse>> verifyLocation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude
    ) {
        if (latitude == null || longitude == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return ResponseEntity.ok(ApiResponse.success("success",
                placeService.verifyLocation(id, latitude, longitude)));
    }

    // 장소 검색 (filter: all | current | archived, 기본값 all)
    @GetMapping("/nearby")
    public ResponseEntity<PlaceNearbyResponse> getNearbyPlaces(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "all") String filter
    ) {
        return ResponseEntity.ok(placeService.getNearbyPlaces(latitude, longitude, radius, query, filter));
    }

    // 인기 장소 (72h 이내 리뷰 2개 이상, 현재 위치에서 가까운 순)
    @GetMapping("/hot")
    public ResponseEntity<ApiResponse<HotPlaceResponse>> getHotPlaces(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success("success",
                placeService.getHotPlaces(latitude, longitude, limit)));
    }

    // 좌표의 행정동 조회 (현재 위치 표시용)
    @GetMapping("/region")
    public ResponseEntity<ApiResponse<RegionResponse>> getRegion(
            @RequestParam double latitude,
            @RequestParam double longitude
    ) {
        return ResponseEntity.ok(ApiResponse.success("success",
                placeService.getRegion(latitude, longitude)));
    }

    // 장소 검색 (카카오 로컬 API + 위치 기반 정렬)
    // keyword 없으면 좌표 기준 주변 장소, 좌표도 없으면 빈 배열
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PlaceNearbyResponse>> searchPlaces(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                placeService.searchPlaces(keyword, latitude, longitude)));
    }

    @PostMapping("/{id}/subscribe")
    public ResponseEntity<ApiResponse<SubscribeResponse>> subscribe(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success("success",
                placeService.subscribe(userId, id)));
    }

    @DeleteMapping("/{id}/subscribe")
    public ResponseEntity<ApiResponse<SubscribeResponse>> unsubscribe(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success("success",
                placeService.unsubscribe(userId, id)));
    }
}
