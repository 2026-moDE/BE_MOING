package com.moing.backend.domain.follow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class FriendRequestResponse {
    @JsonProperty("user_id")
    private Long userId;
    private String nickname;
    @JsonProperty("profile_image_url")
    private String profileImageUrl;
    @JsonProperty("profile_url")
    private String profileUrl;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
