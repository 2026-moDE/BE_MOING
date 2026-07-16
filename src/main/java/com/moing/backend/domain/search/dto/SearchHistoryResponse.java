package com.moing.backend.domain.search.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.search.entity.SearchHistory;

import java.time.LocalDateTime;
import java.util.List;

public record SearchHistoryResponse(List<HistoryItem> history) {

    public record HistoryItem(
            Long id,
            String keyword,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {
        public static HistoryItem from(SearchHistory entity) {
            return new HistoryItem(entity.getId(), entity.getKeyword(), entity.getCreatedAt());
        }
    }
}
