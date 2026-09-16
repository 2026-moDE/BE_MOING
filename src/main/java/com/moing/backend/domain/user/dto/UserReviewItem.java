package com.moing.backend.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.moing.backend.domain.review.entity.CongestionLevel;
import com.moing.backend.domain.review.entity.Visibility;

import java.time.LocalDateTime;

/**
 * 타인 프로필/리뷰 목록에 실리는 리뷰 한 건.
 *
 * <p>조회자가 작성자의 친구인지에 따라 두 필드가 달라진다.
 * <ul>
 *   <li>{@code created_at} - 친구가 아니면 null로 내려간다. 필드 자체는 남긴다
 *       (클라이언트가 키 존재를 전제로 파싱하기 때문).</li>
 *   <li>{@code is_recent} - 친구가 아니면 응답에서 아예 빠진다. 작성 시각을 숨기는데
 *       최근 여부를 남기면 시각이 간접적으로 새기 때문이다.</li>
 * </ul>
 */
@JsonPropertyOrder({"id", "place", "image_url", "thumbnail_url", "thumbnail_small_url",
        "congestion_level", "comment", "visibility", "comment_count", "created_at", "is_recent"})
public record UserReviewItem(
        Long id,
        PlaceInfo place,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("thumbnail_url") String thumbnailUrl,
        @JsonProperty("thumbnail_small_url") String thumbnailSmallUrl,
        @JsonProperty("congestion_level") CongestionLevel congestionLevel,
        String comment,
        // 친구가 아닌 조회자에게는 애초에 전체 공개 리뷰만 내려가므로 항상 PUBLIC이다
        Visibility visibility,
        // 답글과 삭제된(자리표시) 댓글까지 포함한 수. 댓글 목록에 보이는 개수와 같다
        @JsonProperty("comment_count") long commentCount,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("is_recent") Boolean isRecent
) {
    public record PlaceInfo(
            Long id,
            String name,
            String address
    ) {}
}
