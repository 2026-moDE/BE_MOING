package com.moing.backend.domain.piece.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 고정 슬롯 저장 요청.
 *
 * <p>배열 순서가 곧 슬롯 순서다(앞에서부터 1, 2, 3). 보낸 목록이 전체를 덮어쓰므로
 * 빈 배열은 "전부 해제"다.
 *
 * <p>개수(최대 3)·중복 검증은 PieceService가 한다. 하나라도 어긋나면 전체가 롤백된다.
 */
public record PiecePinUpdateRequest(
        @NotNull @JsonProperty("piece_ids") List<Long> pieceIds
) {}
