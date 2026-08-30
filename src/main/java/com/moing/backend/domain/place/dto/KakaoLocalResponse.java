package com.moing.backend.domain.place.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoLocalResponse(
        List<Document> documents
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
            @JsonProperty("place_name") String placeName,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("road_address_name") String roadAddressName,
            @JsonProperty("category_group_code") String categoryGroupCode,
            @JsonProperty("category_group_name") String categoryGroupName,
            String x,   // longitude
            String y     // latitude
    ) {}
}
