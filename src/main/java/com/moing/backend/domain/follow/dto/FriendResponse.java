package com.moing.backend.domain.follow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FriendResponse {
    private Long userId;
    private String nickname;
    private String profileImageUrl;
}
