package com.moing.backend.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"total_users", "today_reviews", "pending_reports", "active_places"})
public record AdminStatsResponse(
        @JsonProperty("total_users") long totalUsers,
        @JsonProperty("today_reviews") long todayReviews,
        @JsonProperty("pending_reports") long pendingReports,
        @JsonProperty("active_places") long activePlaces
) {}
