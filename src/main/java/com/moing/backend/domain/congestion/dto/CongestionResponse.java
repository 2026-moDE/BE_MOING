package com.moing.backend.domain.congestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CongestionResponse(List<AreaItem> areas) {

    public record AreaItem(
            String name,
            @JsonProperty("congestion_level") String congestionLevel,
            Integer population,
            String source
    ) {}
}
