package com.moing.backend.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.LocalDateTime;
import java.util.List;

@JsonPropertyOrder({"places", "next_cursor"})
public record AdminPlaceListResponse(
        List<PlaceItem> places,
        @JsonProperty("next_cursor") Long nextCursor
) {
    @JsonPropertyOrder({"id", "name", "address", "category", "review_count", "is_active", "created_at"})
    public record PlaceItem(
            Long id,
            String name,
            String address,
            String category,
            @JsonProperty("review_count") Long reviewCount,
            @JsonProperty("is_active") Boolean isActive,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}
}
