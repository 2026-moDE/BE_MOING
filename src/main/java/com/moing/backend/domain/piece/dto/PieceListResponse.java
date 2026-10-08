package com.moing.backend.domain.piece.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.moing.backend.domain.piece.entity.PieceVisibility;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 조각 목록. 보드를 한 번에 그려야 해서 페이징 없이 전부 내려준다.
 *
 * <p>내 보드와 친구 보드가 같은 형식을 쓰고, visibility만 다르다. 친구 보드에는 애초에
 * 전체 공개 조각만 실리므로 그 값을 내려봐야 의미가 없어 응답에서 아예 뺀다
 * (null이면 필드가 빠진다).
 */
public record PieceListResponse(
        @JsonProperty("total_count") int totalCount,
        List<PieceItem> pieces
) {
    public record PlaceInfo(
            Long id,
            String name
    ) {}

    @JsonPropertyOrder({"id", "review_id", "image_url", "name", "visibility",
            "position_x", "position_y", "rotation", "scale", "place", "created_at"})
    public record PieceItem(
            Long id,
            @JsonProperty("review_id") Long reviewId,
            @JsonProperty("image_url") String imageUrl,
            String name,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            PieceVisibility visibility,
            // 보드에 아직 올리지 않은 조각은 둘 다 null
            @JsonProperty("position_x") BigDecimal positionX,
            @JsonProperty("position_y") BigDecimal positionY,
            short rotation,
            // 미설정(scale 도입 이전) 조각도 1.00으로 내려간다
            BigDecimal scale,
            PlaceInfo place,
            // 조각을 만든 시각이 아니라 원본 리뷰의 작성 시각
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}
}
