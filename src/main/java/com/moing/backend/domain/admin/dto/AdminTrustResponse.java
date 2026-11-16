package com.moing.backend.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * 신뢰 지표.
 *
 * <p>광고·허위 정보를 AI 검열과 사용자 신고로 걸러낸 결과를 보여준다.
 */
@JsonPropertyOrder({"moderation", "reports"})
public record AdminTrustResponse(
        Moderation moderation,
        Reports reports
) {
    @JsonPropertyOrder({"checked_comments", "suspect_comments", "suspect_rate", "by_category"})
    public record Moderation(
            @JsonProperty("checked_comments") long checkedComments,
            @JsonProperty("suspect_comments") long suspectComments,
            /** suspect_comments / checked_comments (%) */
            @JsonProperty("suspect_rate") double suspectRate,
            @JsonProperty("by_category") List<CategoryCount> byCategory
    ) {}

    @JsonPropertyOrder({"category", "count"})
    public record CategoryCount(
            String category,
            long count
    ) {}

    @JsonPropertyOrder({"reported_reviews", "blinded_reviews", "total_reviews", "blinded_rate"})
    public record Reports(
            @JsonProperty("reported_reviews") long reportedReviews,
            @JsonProperty("blinded_reviews") long blindedReviews,
            /** blinded_rate의 분모 */
            @JsonProperty("total_reviews") long totalReviews,
            /** blinded_reviews / total_reviews (%) */
            @JsonProperty("blinded_rate") double blindedRate
    ) {}
}
