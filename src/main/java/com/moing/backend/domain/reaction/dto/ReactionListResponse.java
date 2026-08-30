package com.moing.backend.domain.reaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ReactionListResponse(
        List<ReactionItem> reactions
) {
    public record UserInfo(
            // 프로필 조회(GET /api/users/{userId}) 호출에 필요하다. 리뷰·댓글 목록 응답과 형태를 맞춘다
            @JsonProperty("user_id") Long userId,
            String nickname,
            @JsonProperty("profile_image_url") String profileImageUrl,
            @JsonProperty("profile_url") String profileUrl
    ) {}

    public record ReactionItem(
            String emoji,
            // 조회한 유저가 남긴 반응인지 여부
            @JsonProperty("is_mine") boolean isMine,
            UserInfo user
    ) {}
}
