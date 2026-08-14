package com.moing.backend.domain.notification.entity;

/**
 * 알림 종류
 * REVIEW: 구독 장소 새 리뷰
 * REACTION: 내 리뷰에 달린 이모지 반응
 * COMMENT: 내 리뷰에 달린 댓글, 내 댓글에 달린 답글
 * FRIEND_REQUEST: 친구 요청
 * FRIEND_ACCEPT: 친구 요청 수락
 * FRIEND_REVIEW: 친구가 남긴 새 리뷰 (실시간이 아니라 모아서 발송할 예정이라 아직 보내는 곳이 없다)
 */
public enum NotificationType {
    REVIEW,
    REACTION,
    COMMENT,
    FRIEND_REQUEST,
    FRIEND_ACCEPT,
    FRIEND_REVIEW
}
