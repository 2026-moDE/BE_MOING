package com.moing.backend.domain.comment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public record CommentListResponse(
        List<CommentItem> comments,
        @JsonProperty("next_cursor") Long nextCursor
) {
    public record UserInfo(
            String nickname,
            @JsonProperty("profile_image_url") String profileImageUrl,
            @JsonProperty("profile_url") String profileUrl
    ) {}

    public record CommentItem(
            Long id,
            @JsonProperty("parent_id") Long parentId,
            // 열람 권한이 없는 비밀 댓글은 null
            String content,
            @JsonProperty("is_secret") boolean isSecret,
            @JsonProperty("is_mine") boolean isMine,
            @JsonProperty("is_deleted") boolean isDeleted,
            UserInfo user,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            @JsonProperty("created_at") LocalDateTime createdAt,
            // 최상위 댓글이면 답글 목록(없으면 빈 배열), 답글 자신이면 null
            List<CommentItem> replies
    ) {}
}
