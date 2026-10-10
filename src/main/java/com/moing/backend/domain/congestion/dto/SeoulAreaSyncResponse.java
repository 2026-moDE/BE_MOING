package com.moing.backend.domain.congestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/** 서울시 지역 목록 동기화 결과 */
public record SeoulAreaSyncResponse(
        @JsonProperty("fetched_count") int fetchedCount,
        @JsonProperty("created_count") int createdCount,
        @JsonProperty("updated_count") int updatedCount,
        @JsonProperty("removed_count") int removedCount,
        @JsonProperty("skipped_count") int skippedCount,
        @JsonProperty("synced_at") LocalDateTime syncedAt
) {}
