package com.moing.backend.domain.place.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.place.entity.PlaceCategory;
import com.moing.backend.domain.review.entity.CongestionLevel;

import java.time.LocalDateTime;

public record PlaceDetailResponse(
        Long id,
        String name,
        String address,
        PlaceCategory category,
        @JsonProperty("business_hours") String businessHours,
        @JsonProperty("is_subscribed") boolean isSubscribed,
        @JsonProperty("congestion_level") CongestionLevel congestionLevel,
        @JsonProperty("congestion_index") Double congestionIndex,
        @JsonProperty("congestion_review_count") int congestionReviewCount,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        @JsonProperty("congestion_updated_at") LocalDateTime congestionUpdatedAt,
        @JsonProperty("review_count") long reviewCount
) {}
