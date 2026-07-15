package com.moing.backend.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public record SubscriptionListResponse(
        List<SubscriptionItem> subscriptions
) {
    public record PlaceInfo(
            Long id,
            String name,
            String address
    ) {}

    public record SubscriptionItem(
            Long id,
            PlaceInfo place,
            @JsonProperty("is_active") boolean isActive,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}
}
