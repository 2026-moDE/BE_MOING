package com.moing.backend.domain.review.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.review.entity.CongestionLevel;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewListResponse(
        List<ReviewItem> reviews,
        @JsonProperty("next_cursor") Long nextCursor
) {
    public record UserInfo(
            String nickname,
            @JsonProperty("profile_image_url") String profileImageUrl
    ) {}

    public record ReviewItem(
            Long id,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("congestion_level") CongestionLevel congestionLevel,
            String comment,
            @JsonProperty("helpful_count") int helpfulCount,
            @JsonProperty("is_helpful") Boolean isHelpful,
            UserInfo user,
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}
}
