package com.moing.backend.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * 공유 패턴 지표.
 *
 * <p>혼잡 정보가 언제, 어떤 상태로 올라오는지를 보여준다. 혼잡한 시간대에 기록이 몰린다는
 * 사실이 서비스가 실제 상황에서 쓰인다는 근거가 된다.
 */
@JsonPropertyOrder({"review_heatmap", "congestion"})
public record AdminPatternsResponse(
        /** KST 요일·시각별 리뷰 수. 기록이 없는 칸도 0으로 채운 168칸 */
        @JsonProperty("review_heatmap") List<HeatmapCell> reviewHeatmap,
        List<Congestion> congestion
) {
    @JsonPropertyOrder({"day_of_week", "hour", "reviews"})
    public record HeatmapCell(
            /** 1=월 ~ 7=일 */
            @JsonProperty("day_of_week") int dayOfWeek,
            int hour,
            long reviews
    ) {}

    @JsonPropertyOrder({"congestion_level", "reviews", "reviews_with_congestion", "rate"})
    public record Congestion(
            @JsonProperty("congestion_level") String congestionLevel,
            long reviews,
            /** rate의 분모 — 혼잡도를 적은 리뷰 수 */
            @JsonProperty("reviews_with_congestion") long reviewsWithCongestion,
            /** reviews / reviews_with_congestion (%) */
            double rate
    ) {}
}
