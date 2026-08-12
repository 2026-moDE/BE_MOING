package com.moing.backend.domain.place.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.place.entity.PlaceCategory;
import com.moing.backend.domain.review.entity.CongestionLevel;

import java.util.List;

public record PlaceNearbyResponse(List<PlaceItem> places) {

    public record PlaceItem(
            Long id,
            String name,
            double latitude,
            double longitude,
            @JsonProperty("congestion_level") CongestionLevel congestionLevel,
            @JsonProperty("thumbnail_url") String thumbnailUrl,
            @JsonProperty("thumbnail_small_url") String thumbnailSmallUrl,
            PlaceCategory category,
            String address
    ) {}
}
