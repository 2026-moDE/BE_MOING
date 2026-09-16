package com.moing.backend.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.LocalDateTime;
import java.util.List;

@JsonPropertyOrder({"reviews", "next_cursor"})
public record AdminReviewListResponse(
        List<ReviewItem> reviews,
        @JsonProperty("next_cursor") Long nextCursor
) {
    @JsonPropertyOrder({"id", "place_name", "image_url", "thumbnail_url", "thumbnail_small_url",
            "congestion_level", "comment", "author_nickname", "status", "comment_count", "created_at"})
    public record ReviewItem(
            Long id,
            @JsonProperty("place_name") String placeName,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("thumbnail_url") String thumbnailUrl,
            @JsonProperty("thumbnail_small_url") String thumbnailSmallUrl,
            @JsonProperty("congestion_level") String congestionLevel,
            String comment,
            @JsonProperty("author_nickname") String authorNickname,
            String status,
            // 답글과 삭제된(자리표시) 댓글까지 포함한 수. 관리자 댓글 목록에 보이는 개수와 같다
            @JsonProperty("comment_count") long commentCount,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}
}
