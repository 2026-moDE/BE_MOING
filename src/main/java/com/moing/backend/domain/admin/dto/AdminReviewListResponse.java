package com.moing.backend.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.LocalDateTime;
import java.util.List;

@JsonPropertyOrder({"reviews", "next_cursor"})
public record AdminReviewListResponse(
        List<ReviewItem> reviews,
        @JsonProperty("next_cursor") Long nextCursor
) {
    @JsonPropertyOrder({"id", "place_name", "image_url", "thumbnail_url", "thumbnail_small_url",
            "congestion_level", "comment", "author_nickname", "status", "created_at"})
    public record ReviewItem(
            Long id,
            @JsonProperty("place_name") String placeName,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("thumbnail_url") String thumbnailUrl,
            @JsonProperty("thumbnail_small_url") String thumbnailSmallUrl,
            @JsonProperty("congestion_level") String congestionLevel,
            String comment,
            @JsonProperty("author_nickname") String authorNickname,
            String status,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}
}
