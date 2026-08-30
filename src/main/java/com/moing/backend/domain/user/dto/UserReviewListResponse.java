package com.moing.backend.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record UserReviewListResponse(
        List<UserReviewItem> reviews,
        @JsonProperty("next_cursor") Long nextCursor
) {}
