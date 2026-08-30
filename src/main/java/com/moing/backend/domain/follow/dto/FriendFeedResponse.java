package com.moing.backend.domain.follow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.review.entity.CongestionLevel;

import java.time.LocalDateTime;
import java.util.List;

public record FriendFeedResponse(List<ReviewItem> reviews) {

    public record UserInfo(
            // 프로필 조회(GET /api/users/{userId}) 호출에 필요하다
            @JsonProperty("user_id") Long userId,
            String nickname,
            @JsonProperty("profile_url") String profileUrl
    ) {}

    public record ReviewItem(
            Long id,
            @JsonProperty("place_id") Long placeId,
            @JsonProperty("place_name") String placeName,
            @JsonProperty("congestion_level") CongestionLevel congestionLevel,
            String comment,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("thumbnail_url") String thumbnailUrl,
            @JsonProperty("thumbnail_small_url") String thumbnailSmallUrl,
            UserInfo user,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}
}
