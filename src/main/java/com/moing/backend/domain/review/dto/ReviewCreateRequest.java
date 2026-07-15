package com.moing.backend.domain.review.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.place.entity.PlaceCategory;
import com.moing.backend.domain.review.entity.CongestionLevel;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ReviewCreateRequest(
        @JsonProperty("place_id") Long placeId,
        @JsonProperty("place_name") String placeName,
        @JsonProperty("place_address") String placeAddress,
        @JsonProperty("place_latitude") BigDecimal placeLatitude,
        @JsonProperty("place_longitude") BigDecimal placeLongitude,
        @JsonProperty("place_category") PlaceCategory placeCategory,
        @JsonProperty("image_url") String imageUrl,
        @NotNull @JsonProperty("congestion_level") CongestionLevel congestionLevel,
        @JsonProperty("quick_tag") String quickTag,
        String comment,
        @NotNull BigDecimal latitude,
        @NotNull BigDecimal longitude
) {}
