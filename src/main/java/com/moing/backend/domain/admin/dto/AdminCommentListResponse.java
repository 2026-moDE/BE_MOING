package com.moing.backend.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.LocalDateTime;
import java.util.List;

@JsonPropertyOrder({"comments", "next_cursor"})
public record AdminCommentListResponse(
        List<CommentItem> comments,
        @JsonProperty("next_cursor") Long nextCursor
) {
    @JsonPropertyOrder({"id", "review_id", "place_name", "content", "author_nickname",
            "is_secret", "is_reply", "status", "created_at"})
    public record CommentItem(
            Long id,
            @JsonProperty("review_id") Long reviewId,
            @JsonProperty("place_name") String placeName,
            // 관리자에게는 비밀 댓글과 내려간 댓글의 원문도 그대로 보여준다
            String content,
            @JsonProperty("author_nickname") String authorNickname,
            @JsonProperty("is_secret") boolean isSecret,
            @JsonProperty("is_reply") boolean isReply,
            // ACTIVE / DELETED
            String status,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {}
}
