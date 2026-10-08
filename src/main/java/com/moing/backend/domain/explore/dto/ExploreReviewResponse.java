package com.moing.backend.domain.explore.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.review.entity.CongestionLevel;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 탐색 탭 리뷰 그리드.
 *
 * <p>그리드 좌표나 카드 크기는 내려주지 않는다. 가로·세로 어떻게 깔지는 클라이언트가 정한다.
 *
 * <p>{@code seed}는 랜덤 순서를 고정하는 값이다. 요청에 없으면 서버가 만들어 여기에 담으니,
 * 클라이언트는 다음 페이지부터 받은 값을 그대로 돌려보내야 같은 순서가 이어진다.
 */
public record ExploreReviewResponse(
        String seed,
        Center center,
        long total,
        // 마지막 페이지면 null
        @JsonProperty("next_offset") Integer nextOffset,
        List<ReviewItem> reviews
) {
    /** 요청받은 기준 좌표와 반경을 그대로 돌려준다 (기본값이 적용된 radius 확인용) */
    public record Center(
            Double latitude,
            Double longitude,
            Integer radius
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
            // 기준 좌표로부터의 거리 (m, 반올림)
            int distance,
            PlaceInfo place,
            UserInfo user,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}
}
