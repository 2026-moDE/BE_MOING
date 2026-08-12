package com.moing.backend.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public record NotificationListResponse(
        List<NotificationItem> notifications,
        @JsonProperty("next_cursor") Long nextCursor
) {
    public record PlaceInfo(
            Long id,
            String name
    ) {}

    public record NotificationItem(
            Long id,
            String type,
            String title,
            String body,
            PlaceInfo place,
            @JsonProperty("is_read") boolean isRead,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}
}
