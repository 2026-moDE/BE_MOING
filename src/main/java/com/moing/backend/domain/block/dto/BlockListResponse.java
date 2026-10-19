package com.moing.backend.domain.block.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 내가 차단한 사용자 목록.
 *
 * <p>차단 해제 화면이 쓰는 목록이라 페이지네이션 없이 전부 내려준다.
 */
public record BlockListResponse(List<BlockItem> blocks) {

    public record BlockItem(
            @JsonProperty("user_id") Long userId,
            String nickname,
            @JsonProperty("profile_image_url") String profileImageUrl,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            @JsonProperty("blocked_at") LocalDateTime blockedAt
    ) {}
}
