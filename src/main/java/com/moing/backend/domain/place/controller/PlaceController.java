package com.moing.backend.domain.place.controller;

import com.moing.backend.domain.place.dto.PlaceNearbyResponse;
import com.moing.backend.domain.place.service.PlaceService;
import com.moing.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceService placeService;

    // 주변 장소 목록 조회
    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<PlaceNearbyResponse>> getNearbyPlaces(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "1500") int radius,
            @RequestParam String query
    ) {
        return ResponseEntity.ok(ApiResponse.success("success", placeService.getNearbyPlaces(latitude, longitude, radius, query)));
    }
}
