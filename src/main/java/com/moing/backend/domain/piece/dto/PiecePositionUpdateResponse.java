package com.moing.backend.domain.piece.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PiecePositionUpdateResponse(
        @JsonProperty("updated_count") int updatedCount
) {}
