package com.moing.backend.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 미읽음 알림 존재 여부. 알림함 아이콘에 점을 띄울지 판단하는 용도라
 * 개수는 담지 않는다 (개수가 필요해지면 목록 API를 쓴다).
 */
public record UnreadNotificationResponse(
        @JsonProperty("has_unread") boolean hasUnread
) {}
