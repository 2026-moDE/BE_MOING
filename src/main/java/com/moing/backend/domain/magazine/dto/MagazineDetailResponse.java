package com.moing.backend.domain.magazine.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.review.entity.CongestionLevel;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 매거진 풀스크린 뷰어. 아이템을 한 장씩 넘겨본다.
 *
 * <p>순서는 배치가 정한 {@code sort_order}를 그대로 따른다.
 */
public record MagazineDetailResponse(
        Long id,
        String title,
        List<Item> items
) {
    public record Item(
            @JsonProperty("review_id") Long reviewId,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("thumbnail_url") String thumbnailUrl,
            String comment,
            @JsonProperty("congestion_level") CongestionLevel congestionLevel,

            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            @JsonProperty("created_at") LocalDateTime createdAt,

            /** 요청 좌표로부터의 거리(m, 반올림). 좌표를 안 주면 null */
            Integer distance,

            PlaceInfo place,
            UserInfo user
    ) {}

    public record PlaceInfo(
            Long id,
            String name,
            String address
    ) {}

    public record UserInfo(
            // 프로필 조회(GET /api/users/{userId}) 호출에 필요하다. 리뷰 목록 응답과 형태를 맞춘다
            @JsonProperty("user_id") Long userId,
            String nickname,
            @JsonProperty("profile_image_url") String profileImageUrl
    ) {}
}
