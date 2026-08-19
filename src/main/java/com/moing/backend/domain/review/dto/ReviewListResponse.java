package com.moing.backend.domain.review.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.review.entity.CongestionLevel;
import com.moing.backend.domain.review.entity.Visibility;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewListResponse(
        List<ReviewItem> reviews,
        @JsonProperty("next_cursor") Long nextCursor
) {
    public record UserInfo(
            String nickname,
            @JsonProperty("profile_image_url") String profileImageUrl,
            @JsonProperty("profile_url") String profileUrl
    ) {}

    public record ReviewItem(
            Long id,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("thumbnail_url") String thumbnailUrl,
            @JsonProperty("thumbnail_small_url") String thumbnailSmallUrl,
            @JsonProperty("congestion_level") CongestionLevel congestionLevel,
            String comment,
            @JsonProperty("is_mine") boolean isMine,
            Visibility visibility,
            @JsonProperty("is_friend") boolean isFriend,
            UserInfo user,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}
}
