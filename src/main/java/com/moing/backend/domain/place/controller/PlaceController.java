package com.moing.backend.domain.place.controller;

import com.moing.backend.domain.place.dto.AutocompleteResponse;
import com.moing.backend.domain.place.dto.PlaceNearbyResponse;
import com.moing.backend.domain.place.dto.PlaceSearchResponse;
import com.moing.backend.domain.place.entity.PlaceCategory;
import com.moing.backend.domain.place.service.PlaceService;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
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

    @GetMapping("/search/autocomplete")
    public ResponseEntity<AutocompleteResponse> autocomplete(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String keyword
    ) {
        if (!StringUtils.hasText(keyword)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return ResponseEntity.ok(placeService.autocomplete(keyword));
    }

    @GetMapping("/search")
    public ResponseEntity<PlaceSearchResponse> searchPlaces(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String keyword
    ) {
        if (!StringUtils.hasText(keyword)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return ResponseEntity.ok(placeService.searchPlaces(userId, keyword));
    }
}
