package com.moing.backend.domain.user.controller;

import com.moing.backend.domain.user.dto.MyReviewListResponse;
import com.moing.backend.domain.user.dto.NotificationListResponse;
import com.moing.backend.domain.user.dto.UserProfileResponse;
import com.moing.backend.domain.user.dto.UserPublicProfileResponse;
import com.moing.backend.domain.user.dto.UserReviewListResponse;
import com.moing.backend.domain.user.dto.SubscriptionListResponse;
import com.moing.backend.domain.user.dto.UnreadNotificationResponse;
import com.moing.backend.domain.user.dto.UserUpdateRequest;
import com.moing.backend.domain.user.dto.UserUpdateResponse;
import com.moing.backend.domain.user.service.UserService;
import com.moing.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success("success", userService.getMyProfile(userId)));
    }

    @GetMapping("/me/reviews")
    public ResponseEntity<ApiResponse<MyReviewListResponse>> getMyReviews(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        return ResponseEntity.ok(ApiResponse.success("success",
                userService.getMyReviews(userId, cursor, limit, year, month)));
    }

    @GetMapping("/me/notifications")
    public ResponseEntity<ApiResponse<NotificationListResponse>> getMyNotifications(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success("success",
                userService.getMyNotifications(userId, cursor, limit)));
    }

    @GetMapping("/me/notifications/unread")
    public ResponseEntity<ApiResponse<UnreadNotificationResponse>> hasUnreadNotifications(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success("success",
                userService.hasUnreadNotifications(userId)));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserUpdateResponse>> updateMyProfile(
            @AuthenticationPrincipal Long userId,
            @RequestBody UserUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("success",
                userService.updateMyProfile(userId, request)));
    }

    // "/me"는 리터럴이라 "/{userId}"보다 먼저 매칭된다
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserPublicProfileResponse>> getUserProfile(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success("success",
                userService.getUserProfile(viewerId, userId)));
    }

    @GetMapping("/{userId}/reviews")
    public ResponseEntity<ApiResponse<UserReviewListResponse>> getUserReviews(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success("success",
                userService.getUserReviews(viewerId, userId, cursor, limit)));
    }

    @GetMapping("/me/subscriptions")
    public ResponseEntity<ApiResponse<SubscriptionListResponse>> getMySubscriptions(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success("success",
                userService.getMySubscriptions(userId)));
    }
}
