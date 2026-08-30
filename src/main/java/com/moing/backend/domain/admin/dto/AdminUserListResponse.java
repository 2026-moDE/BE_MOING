package com.moing.backend.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.LocalDateTime;
import java.util.List;

@JsonPropertyOrder({"users", "next_cursor"})
public record AdminUserListResponse(
        List<UserItem> users,
        @JsonProperty("next_cursor") Long nextCursor
) {
    @JsonPropertyOrder({"id", "nickname", "email", "profile_image_url", "review_count", "created_at"})
    public record UserItem(
            Long id,
            String nickname,
            String email,
            @JsonProperty("profile_image_url") String profileImageUrl,
            @JsonProperty("review_count") long reviewCount,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}
}
