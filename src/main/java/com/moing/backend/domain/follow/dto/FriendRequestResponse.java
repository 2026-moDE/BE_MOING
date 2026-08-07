package com.moing.backend.domain.follow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class FriendRequestResponse {
    private Long userId;
    private String nickname;
    private String profileImageUrl;
    private LocalDateTime createdAt;
}
