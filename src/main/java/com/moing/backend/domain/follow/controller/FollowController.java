package com.moing.backend.domain.follow.controller;

import com.moing.backend.domain.follow.dto.FriendFeedResponse;
import com.moing.backend.domain.follow.dto.FriendRequestResponse;
import com.moing.backend.domain.follow.dto.FriendResponse;
import com.moing.backend.domain.follow.dto.UserSearchResponse;
import com.moing.backend.domain.follow.service.FollowService;
import com.moing.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @PostMapping("/request/{userId}")
    public ResponseEntity<ApiResponse<Void>> sendFollowRequest(
            @AuthenticationPrincipal Long myId,
            @PathVariable Long userId
    ) {
        followService.sendFollowRequest(myId, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("친구 요청을 보냈습니다", null));
    }

    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<List<FriendRequestResponse>>> getReceivedRequests(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(followService.getReceivedRequests(userId)));
    }

    @PatchMapping("/requests/{userId}/accept")
    public ResponseEntity<ApiResponse<Void>> acceptRequest(
            @AuthenticationPrincipal Long myId,
            @PathVariable Long userId
    ) {
        followService.acceptRequest(myId, userId);
        return ResponseEntity.ok(ApiResponse.success("친구 요청을 수락했습니다", null));
    }

    @PatchMapping("/requests/{userId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectRequest(
            @AuthenticationPrincipal Long myId,
            @PathVariable Long userId
    ) {
        followService.rejectRequest(myId, userId);
        return ResponseEntity.ok(ApiResponse.success("친구 요청을 거절했습니다", null));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> cancelFollow(
            @AuthenticationPrincipal Long myId,
            @PathVariable Long userId
    ) {
        followService.cancelFollow(myId, userId);
        return ResponseEntity.ok(ApiResponse.success("친구를 삭제했습니다", null));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<UserSearchResponse>>> searchUsers(
            @AuthenticationPrincipal Long userId,
            @RequestParam String nickname
    ) {
        return ResponseEntity.ok(ApiResponse.success(followService.searchByNickname(userId, nickname)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FriendResponse>>> getFriends(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(followService.getFriends(userId)));
    }

    // 친구 최근 리뷰 피드 (72h 이내, 최신순)
    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<FriendFeedResponse>> getFriendFeed(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(followService.getFriendFeed(userId, limit)));
    }
}
