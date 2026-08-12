package com.moing.backend.domain.comment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.comment.entity.Comment;

import java.time.LocalDateTime;

public record CommentCreateResponse(
        Long id,
        @JsonProperty("review_id") Long reviewId,
        @JsonProperty("parent_id") Long parentId,
        String content,
        @JsonProperty("is_secret") boolean isSecret,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        @JsonProperty("created_at") LocalDateTime createdAt
) {
    public static CommentCreateResponse from(Comment comment) {
        return new CommentCreateResponse(
                comment.getId(),
                comment.getReviewId(),
                comment.getParentId(),
                comment.getContent(),
                comment.isSecret(),
                comment.getCreatedAt()
        );
    }
}
