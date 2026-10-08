package com.moing.backend.domain.piece.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PieceCreateResponse(
        @JsonProperty("piece_id") Long pieceId
) {}
