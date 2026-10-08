package com.moing.backend.domain.piece.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.piece.entity.PieceVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PieceCreateRequest(
        @NotNull @JsonProperty("review_id") Long reviewId,
        // 누끼 PNG는 프론트가 S3에 먼저 올리고 그 URL만 보낸다
        @NotBlank @Size(max = 500) @JsonProperty("image_url") String imageUrl,
        @Size(max = 30) String name,
        // 미지정 시 PUBLIC(친구에게 공개)
        PieceVisibility visibility
) {}
