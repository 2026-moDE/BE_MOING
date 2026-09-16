package com.moing.backend.domain.review.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.review.entity.CongestionLevel;
import com.moing.backend.domain.review.entity.Visibility;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewListResponse(
        List<ReviewItem> reviews,
        @JsonProperty("next_cursor") Long nextCursor
) {
    public record UserInfo(
            // 프로필 조회(GET /api/users/{userId}) 호출에 필요하다. 상세 조회 응답과 형태를 맞춘다
            @JsonProperty("user_id") Long userId,
            String nickname,
            @JsonProperty("profile_image_url") String profileImageUrl,
            @JsonProperty("profile_url") String profileUrl
    ) {}

    public record ReviewItem(
            Long id,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("thumbnail_url") String thumbnailUrl,
            @JsonProperty("thumbnail_small_url") String thumbnailSmallUrl,
            @JsonProperty("congestion_level") CongestionLevel congestionLevel,
            String comment,
            @JsonProperty("is_mine") boolean isMine,
            Visibility visibility,
            @JsonProperty("is_friend") boolean isFriend,
            // 답글과 삭제된(자리표시) 댓글까지 포함한 수. 댓글 목록에 보이는 개수와 같다
            @JsonProperty("comment_count") long commentCount,
            UserInfo user,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}
}
