package com.moing.backend.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class AdminPlaceUpdateRequest {

    @JsonProperty("is_active")
    private Boolean active;

    private String category;
}
