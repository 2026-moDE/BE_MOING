package com.moing.backend.domain.place.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.place.entity.PlaceCategory;
import com.moing.backend.domain.review.entity.CongestionLevel;

public record PlaceDetailResponse(
        Long id,
        String name,
        String address,
        PlaceCategory category,
        @JsonProperty("business_hours") String businessHours,
        @JsonProperty("is_subscribed") boolean isSubscribed,
        @JsonProperty("congestion_level") CongestionLevel congestionLevel,
        @JsonProperty("review_count") long reviewCount
) {}
