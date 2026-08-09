package com.moing.backend.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"id", "nickname", "email", "profile_image_url", "profile_url", "review_count", "place_count", "subscription_count", "friend_count"})
public record UserProfileResponse(
        Long id,
        String nickname,
        String email,
        @JsonProperty("profile_image_url") String profileImageUrl,
        @JsonProperty("profile_url") String profileUrl,
        @JsonProperty("review_count") long reviewCount,
        @JsonProperty("place_count") long placeCount,
        @JsonProperty("subscription_count") long subscriptionCount,
        @JsonProperty("friend_count") long friendCount
) {}
