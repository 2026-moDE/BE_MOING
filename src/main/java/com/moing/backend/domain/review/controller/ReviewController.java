package com.moing.backend.domain.review.controller;

import com.moing.backend.domain.review.dto.ReviewCreateRequest;
import com.moing.backend.domain.review.dto.ReviewCreateResponse;
import com.moing.backend.domain.review.dto.ReviewDetailResponse;
import com.moing.backend.domain.review.dto.ReviewReportRequest;
import com.moing.backend.domain.review.dto.ReviewUpdateRequest;
import com.moing.backend.domain.review.service.ReviewService;
import com.moing.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    // 리뷰 상세 조회 (친구 공개 리뷰는 작성자 본인과 친구만 접근 가능)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReviewDetailResponse>> getReviewDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success("success", reviewService.getReviewDetail(userId, id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody ReviewUpdateRequest request
    ) {
        reviewService.updateReview(userId, id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        reviewService.deleteReview(userId, id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/report")
    public ResponseEntity<ApiResponse<Void>> reportReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody ReviewReportRequest request
    ) {
        reviewService.reportReview(userId, id, request);
        return ResponseEntity.ok(ApiResponse.success("신고가 접수되었습니다", null));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewCreateResponse>> createReview(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ReviewCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("success", reviewService.createReview(userId, request)));
    }
}
