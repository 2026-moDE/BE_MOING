package com.moing.backend.domain.place.controller;

import com.moing.backend.domain.place.dto.PlaceNearbyResponse;
import com.moing.backend.domain.place.entity.PlaceCategory;
import com.moing.backend.domain.place.service.PlaceService;
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

    // JWT 인증된 사용자만 접근 가능 (JwtAuthenticationFilter + SecurityConfig에서 보호)
    @GetMapping("/nearby")
    public ResponseEntity<PlaceNearbyResponse> getNearbyPlaces(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "1500") int radius,
            @RequestParam String query
    ) {
        return ResponseEntity.ok(placeService.getNearbyPlaces(latitude, longitude, radius, query));
    }
}
