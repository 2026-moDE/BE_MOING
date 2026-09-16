package com.moing.backend.domain.review.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.review.entity.CongestionLevel;
import com.moing.backend.domain.review.entity.Visibility;

import java.time.LocalDateTime;

public record ReviewDetailResponse(
        Long id,
        @JsonProperty("place_id") Long placeId,
        @JsonProperty("place_name") String placeName,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("thumbnail_url") String thumbnailUrl,
        @JsonProperty("thumbnail_small_url") String thumbnailSmallUrl,
        @JsonProperty("congestion_level") CongestionLevel congestionLevel,
        String comment,
        @JsonProperty("is_mine") boolean isMine,
        Visibility visibility,
        // 리뷰 작성자와 친구(ACCEPTED)인지 여부. 본인 리뷰면 false
        @JsonProperty("is_friend") boolean isFriend,
        // 답글과 삭제된(자리표시) 댓글까지 포함한 수. 댓글 목록에 보이는 개수와 같다
        @JsonProperty("comment_count") long commentCount,
        UserInfo user,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        @JsonProperty("created_at") LocalDateTime createdAt
) {
    public record UserInfo(
            @JsonProperty("user_id") Long userId,
            String nickname,
            @JsonProperty("profile_image_url") String profileImageUrl,
            @JsonProperty("profile_url") String profileUrl
    ) {}
}
