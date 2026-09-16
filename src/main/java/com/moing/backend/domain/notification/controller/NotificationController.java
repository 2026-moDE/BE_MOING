package com.moing.backend.domain.notification.controller;

import com.moing.backend.domain.notification.service.NotificationService;
import com.moing.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        notificationService.markAsRead(userId, id);
        return ResponseEntity.ok(ApiResponse.success("알림을 읽음 처리했습니다", null));
    }

    /**
     * 알림함 방문 기록.
     *
     * <p>알림 아이콘의 점을 끄는 용도다. 개별 읽음(is_read)과는 별개로,
     * 이 시각 이후에 도착한 알림이 있으면 아이콘이 다시 켜진다.
     * 목록 조회(GET)가 아니라 이 엔드포인트만 시각을 갱신하므로,
     * 페이징이나 화면 재조회로 방문 시각이 밀리지 않는다.
     */
    @PostMapping("/visit")
    public ResponseEntity<ApiResponse<Void>> markVisited(
            @AuthenticationPrincipal Long userId
    ) {
        notificationService.markVisited(userId);
        return ResponseEntity.ok(ApiResponse.success("알림함 방문을 기록했습니다", null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        notificationService.delete(userId, id);
        return ResponseEntity.ok(ApiResponse.success("알림을 삭제했습니다", null));
    }
}
