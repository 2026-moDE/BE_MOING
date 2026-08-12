package com.moing.backend.domain.reaction.dto;

/**
 * 이모지별 집계 결과 (JPQL 조회 전용)
 */
public record EmojiCount(String emoji, Long count) {}
