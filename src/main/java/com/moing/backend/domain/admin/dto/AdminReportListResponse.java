package com.moing.backend.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.LocalDateTime;
import java.util.List;

@JsonPropertyOrder({"reports", "next_cursor"})
public record AdminReportListResponse(
        List<ReportItem> reports,
        @JsonProperty("next_cursor") Long nextCursor
) {
    @JsonPropertyOrder({"id", "review_id", "review_image_url", "reason", "detail",
            "reporter_nickname", "status", "created_at"})
    public record ReportItem(
            Long id,
            @JsonProperty("review_id") Long reviewId,
            @JsonProperty("review_image_url") String reviewImageUrl,
            String reason,
            String detail,
            @JsonProperty("reporter_nickname") String reporterNickname,
            String status,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}
}
