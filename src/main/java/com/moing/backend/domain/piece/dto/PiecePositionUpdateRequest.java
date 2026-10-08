package com.moing.backend.domain.piece.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * 보드 배치 벌크 저장 요청.
 *
 * <p>드래그를 끝낸 뒤 보드 전체를 한 번에 저장하는 용도라 한 트랜잭션으로 처리한다.
 * 값 범위(위치 0~1, 기울기 -16~16) 검증은 PieceService가 하고, 하나라도 어긋나면
 * 전체가 롤백된다.
 */
public record PiecePositionUpdateRequest(
        @NotNull @Valid List<PositionItem> positions
) {
    public record PositionItem(
            @NotNull @JsonProperty("piece_id") Long pieceId,
            @JsonProperty("position_x") BigDecimal positionX,
            @JsonProperty("position_y") BigDecimal positionY,
            // 생략하면 회전 없음(0)으로 덮어쓴다
            Integer rotation
    ) {}
}
