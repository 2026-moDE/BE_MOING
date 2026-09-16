package com.moing.backend.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.review.entity.CongestionLevel;
import com.moing.backend.domain.review.entity.Visibility;

import java.time.LocalDateTime;
import java.util.List;

public record MyReviewListResponse(
        List<MyReviewItem> reviews,
        @JsonProperty("next_cursor") Long nextCursor
) {
    public record PlaceInfo(
            Long id,
            String name,
            String address
    ) {}

    public record MyReviewItem(
            Long id,
            PlaceInfo place,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("thumbnail_url") String thumbnailUrl,
            @JsonProperty("thumbnail_small_url") String thumbnailSmallUrl,
            @JsonProperty("congestion_level") CongestionLevel congestionLevel,
            String comment,
            // 옛 리뷰는 값이 없을 수 있어 PUBLIC으로 채워 내린다 (다른 응답과 같은 규칙)
            Visibility visibility,
            // 답글과 삭제된(자리표시) 댓글까지 포함한 수. 댓글 목록에 보이는 개수와 같다
            @JsonProperty("comment_count") long commentCount,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}
}
