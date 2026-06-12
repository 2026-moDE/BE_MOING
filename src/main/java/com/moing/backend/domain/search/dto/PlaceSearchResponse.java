package com.moing.backend.domain.search.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.place.entity.PlaceCategory;
import com.moing.backend.domain.review.entity.CongestionLevel;

import java.util.List;

public record PlaceSearchResponse(List<PlaceItem> places) {

    public record PlaceItem(
            Long id,
            String name,
            String address,
            PlaceCategory category,
            double latitude,
            double longitude,
            @JsonProperty("congestion_level") CongestionLevel congestionLevel,
            @JsonProperty("thumbnail_url") String thumbnailUrl
    ) {}
}
