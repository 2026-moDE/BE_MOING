package com.moing.backend.domain.review.entity;

/**
 * 리뷰 공개 범위
 * PUBLIC  - 전체 공개, 누구나 댓글 작성 가능
 * FRIENDS - 친구 공개, 친구만 댓글/답글 작성 가능
 */
public enum Visibility {
    PUBLIC,
    FRIENDS
}
