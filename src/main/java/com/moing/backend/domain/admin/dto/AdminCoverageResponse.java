package com.moing.backend.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 커버리지 지표.
 *
 * <p>사용자 기록이 닿은 범위와, 공공데이터로 메운 범위를 나란히 보여준다.
 * 사용자 기록이 적은 지역을 서울시 실시간 도시데이터가 받치는 구조라는 근거가 된다.
 */
@JsonPropertyOrder({"user_report_places", "public_data_points", "last_synced_at",
        "regions", "sigungu_top", "categories"})
public record AdminCoverageResponse(
        /** 실사용자 리뷰가 1건 이상 있는 장소 수 */
        @JsonProperty("user_report_places") long userReportPlaces,
        /** 서울시 실시간 도시데이터로 혼잡도를 제공하는 지점 수 */
        @JsonProperty("public_data_points") long publicDataPoints,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        @JsonProperty("last_synced_at") LocalDateTime lastSyncedAt,
        List<Region> regions,
        @JsonProperty("sigungu_top") List<Sigungu> sigunguTop,
        List<Category> categories
) {
    @JsonPropertyOrder({"region_group", "places", "reviews"})
    public record Region(
            /** 서울 / 수도권(경기·인천) / 비수도권 */
            @JsonProperty("region_group") String regionGroup,
            long places,
            long reviews
    ) {}

    @JsonPropertyOrder({"sido", "sigungu", "places", "reviews"})
    public record Sigungu(
            String sido,
            String sigungu,
            long places,
            long reviews
    ) {}

    @JsonPropertyOrder({"category", "places", "reviews"})
    public record Category(
            String category,
            long places,
            long reviews
    ) {}
}
