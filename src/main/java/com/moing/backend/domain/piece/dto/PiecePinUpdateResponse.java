package com.moing.backend.domain.piece.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 고정 슬롯 저장 결과. pins는 저장된 슬롯 순서대로 실린다. */
public record PiecePinUpdateResponse(
        @JsonProperty("pinned_count") int pinnedCount,
        List<PinItem> pins
) {
    public record PinItem(
            @JsonProperty("piece_id") Long pieceId,
            @JsonProperty("pin_order") short pinOrder
    ) {}
}
