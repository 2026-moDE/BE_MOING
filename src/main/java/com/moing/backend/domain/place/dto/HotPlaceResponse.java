package com.moing.backend.domain.place.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.place.entity.PlaceCategory;
import com.moing.backend.domain.review.entity.CongestionLevel;

import java.time.LocalDateTime;
import java.util.List;

public record HotPlaceResponse(List<PlaceItem> places) {

    public record PlaceItem(
            Long id,
            String name,
            PlaceCategory category,
            @JsonProperty("congestion_level") CongestionLevel congestionLevel,
            @JsonProperty("review_count") int reviewCount,
            @JsonProperty("review_images") List<ReviewImage> reviewImages,
            @JsonProperty("latest_review") LatestReview latestReview
    ) {}

    /** 리뷰 사진 (최신순). 사진이 없는 장소는 빈 배열 */
    public record ReviewImage(
            @JsonProperty("thumbnail_url") String thumbnailUrl,
            @JsonProperty("thumbnail_small_url") String thumbnailSmallUrl
    ) {}

    public record LatestReview(
            String comment,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}
}
