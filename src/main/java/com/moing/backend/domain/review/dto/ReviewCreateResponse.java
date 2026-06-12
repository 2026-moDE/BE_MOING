package com.moing.backend.domain.review.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.review.entity.Review;

import java.time.LocalDateTime;

public record ReviewCreateResponse(ReviewItem review) {

    public record ReviewItem(
            Long id,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}

    public static ReviewCreateResponse from(Review review) {
        return new ReviewCreateResponse(new ReviewItem(review.getId(), review.getCreatedAt()));
    }
}
