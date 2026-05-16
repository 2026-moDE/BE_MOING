package com.moing.backend.domain.review.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.global.infra.S3Service;
import com.moing.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/s3")
@RequiredArgsConstructor
public class S3Controller {

    private final S3Service s3Service;

    // 이미지 업로드 URL 발급
    @PostMapping("/presigned-url")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> getPresignedUrl(
            @AuthenticationPrincipal Long userId,
            @RequestBody PresignedUrlRequest request
    ) {
        S3Service.PresignedUrlResult result = s3Service.generatePresignedUrl(
                request.fileName(), request.contentType());
        return ResponseEntity.ok(ApiResponse.success("success",
                new PresignedUrlResponse(result.presignedUrl(), result.imageUrl())));
    }

    public record PresignedUrlRequest(
            @JsonProperty("file_name") String fileName,
            @JsonProperty("content_type") String contentType
    ) {}

    public record PresignedUrlResponse(
            @JsonProperty("presigned_url") String presignedUrl,
            @JsonProperty("image_url") String imageUrl
    ) {}
}
