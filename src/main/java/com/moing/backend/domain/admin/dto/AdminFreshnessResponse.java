package com.moing.backend.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.LocalDate;
import java.util.List;

/**
 * 최신성 지표.
 *
 * <p>지금 지도에서 보이는 정보가 얼마나 최신인지를 보여준다. 오래된 장소 정보라는 문제에
 * 노출 시간을 끊는 방식으로 답했다는 근거가 된다.
 */
@JsonPropertyOrder({"visible_reviews_now", "median_age_hours_now", "avg_age_hours_now",
        "daily_visible", "max_exposure_hours"})
public record AdminFreshnessResponse(
        @JsonProperty("visible_reviews_now") long visibleReviewsNow,
        /** 노출 중인 리뷰의 작성 후 경과 시간 중앙값. 노출 0건이면 null */
        @JsonProperty("median_age_hours_now") Double medianAgeHoursNow,
        /** 노출 중인 리뷰의 작성 후 경과 시간 평균. 노출 0건이면 null */
        @JsonProperty("avg_age_hours_now") Double avgAgeHoursNow,
        @JsonProperty("daily_visible") List<DailyVisible> dailyVisible,
        @JsonProperty("max_exposure_hours") int maxExposureHours
) {
    @JsonPropertyOrder({"date", "visible_reviews", "median_age_hours"})
    public record DailyVisible(
            /** 기준 날짜 (KST) */
            @JsonFormat(pattern = "yyyy-MM-dd")
            LocalDate date,
            @JsonProperty("visible_reviews") long visibleReviews,
            /** 그 시점 노출 중이던 리뷰의 경과 시간 중앙값. 0건이면 null */
            @JsonProperty("median_age_hours") Double medianAgeHours
    ) {}
}
