package com.moing.backend.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserUpdateResponse(
        String nickname,
        @JsonProperty("profile_image_url") String profileImageUrl
) {}
