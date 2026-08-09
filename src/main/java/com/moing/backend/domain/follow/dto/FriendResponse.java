package com.moing.backend.domain.follow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FriendResponse {
    @JsonProperty("user_id")
    private Long userId;
    private String nickname;
    @JsonProperty("profile_image_url")
    private String profileImageUrl;
}
