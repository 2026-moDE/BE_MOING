package com.moing.backend.domain.place.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SubscribeResponse(
        @JsonProperty("is_subscribed") boolean isSubscribed
) {}
