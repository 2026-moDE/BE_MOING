package com.moing.backend.domain.follow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserSearchResponse {
    @JsonProperty("user_id")
    private Long userId;
    private String nickname;
    @JsonProperty("profile_image_url")
    private String profileImageUrl;
    @JsonProperty("friend_status")
    private String friendStatus; // NONE, PENDING, ACCEPTED
}
