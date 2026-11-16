package com.moing.backend.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.LocalDate;
import java.util.List;

/**
 * 참여 지표.
 *
 * <p>현장에서만 쓸 수 있다는 제약을 두고도 사람들이 실제로 기록을 남기는지를 보여준다.
 * 비율만 내려주면 표본이 작을 때 과장돼 보이므로 분모와 분자를 함께 담는다.
 */
@JsonPropertyOrder({"total_users", "writers", "activation_rate", "repeat_writers", "repeat_rate",
        "avg_reviews_per_writer", "total_reviews", "weekly_active"})
public record AdminParticipationResponse(
        @JsonProperty("total_users") long totalUsers,
        long writers,
        /** writers / total_users (%) */
        @JsonProperty("activation_rate") double activationRate,
        @JsonProperty("repeat_writers") long repeatWriters,
        /** repeat_writers / writers (%) */
        @JsonProperty("repeat_rate") double repeatRate,
        @JsonProperty("avg_reviews_per_writer") double avgReviewsPerWriter,
        @JsonProperty("total_reviews") long totalReviews,
        @JsonProperty("weekly_active") List<WeeklyActive> weeklyActive
) {
    @JsonPropertyOrder({"week_start", "active_users"})
    public record WeeklyActive(
            /** 그 주의 월요일 (KST) */
            @JsonFormat(pattern = "yyyy-MM-dd")
            @JsonProperty("week_start") LocalDate weekStart,
            @JsonProperty("active_users") long activeUsers
    ) {}
}
