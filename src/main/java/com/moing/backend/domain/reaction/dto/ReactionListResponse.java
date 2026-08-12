package com.moing.backend.domain.reaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ReactionListResponse(
        List<ReactionItem> reactions
) {
    public record ReactionItem(
            String emoji,
            long count,
            // 조회한 유저가 해당 이모지를 남겼는지 여부
            @JsonProperty("is_mine") boolean isMine
    ) {}
}
