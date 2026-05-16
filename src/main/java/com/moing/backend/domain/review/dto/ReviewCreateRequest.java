package com.moing.backend.domain.review.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.review.entity.CongestionLevel;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ReviewCreateRequest(
        @NotNull @JsonProperty("place_id") Long placeId,
        @JsonProperty("image_url") String imageUrl,
        @NotNull @JsonProperty("congestion_level") CongestionLevel congestionLevel,
        @JsonProperty("quick_tag") String quickTag,
        String comment,
        @NotNull BigDecimal latitude,
        @NotNull BigDecimal longitude
) {}
