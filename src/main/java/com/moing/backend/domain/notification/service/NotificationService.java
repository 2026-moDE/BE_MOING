package com.moing.backend.domain.notification.service;

import com.moing.backend.domain.notification.entity.Notification;
import com.moing.backend.domain.notification.entity.NotificationType;
import com.moing.backend.domain.notification.repository.NotificationRepository;
import com.moing.backend.domain.user.entity.User;
import com.moing.backend.domain.user.repository.UserRepository;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import com.moing.backend.global.infra.FcmService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FcmService fcmService;

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        // 본인 알림이 아니면 403
        if (!notification.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        notification.markAsRead();
    }

    // 알림 저장 + FCM 발송. 탈퇴한 유저에게는 보내지 않는다
    @Transactional
    public void send(Long targetUserId, NotificationType type, Long placeId, Long reviewId,
                     String title, String body) {
        userRepository.findById(targetUserId)
                .ifPresent(target -> send(target, type, placeId, reviewId, title, body));
    }

    // 대상 유저를 이미 조회해 둔 경우 (여러 명에게 보낼 때 재조회를 피한다)
    @Transactional
    public void send(User target, NotificationType type, Long placeId, Long reviewId,
                     String title, String body) {
        notificationRepository.save(Notification.builder()
                .userId(target.getId())
                .placeId(placeId)
                .reviewId(reviewId)
                .type(type)
                .title(title)
                .body(body)
                .build());

        if (target.getFcmToken() != null) {
            fcmService.sendNotification(target.getFcmToken(), title, body);
        }
    }
}
