package com.moing.backend.domain.piece.dto;

import com.moing.backend.domain.piece.entity.PieceVisibility;
import jakarta.validation.constraints.Size;

/**
 * 조각 수정 요청. 보낸 필드만 바뀐다(생략하면 기존 값 유지).
 *
 * <p>name에 빈 문자열을 보내면 이름을 지운 것으로 본다 (프로필 수정과 같은 규칙).
 */
public record PieceUpdateRequest(
        @Size(max = 30) String name,
        PieceVisibility visibility
) {}
