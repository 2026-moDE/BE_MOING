package com.moing.backend.domain.congestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.review.entity.CongestionLevel;

import java.util.List;

public record CongestionResponse(List<AreaItem> areas) {

    public record AreaItem(
            String name,
            @JsonProperty("congestion_level") CongestionLevel congestionLevel,
            @JsonProperty("congestion_label") String congestionLabel,
            @JsonProperty("bubble_color") CongestionLevel.BubbleColor bubbleColor,
            Integer population,
            String source
    ) {
        // 라벨·색상은 항상 혼잡도 단계에서 파생시켜 값이 어긋나지 않게 한다
        public static AreaItem of(String name, CongestionLevel level, Integer population, String source) {
            return new AreaItem(name, level, level.getLabel(), level.getBubbleColor(), population, source);
        }
    }
}
