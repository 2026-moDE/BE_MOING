package com.moing.backend.domain.piece.controller;

import com.moing.backend.domain.piece.dto.PieceCreateRequest;
import com.moing.backend.domain.piece.dto.PieceCreateResponse;
import com.moing.backend.domain.piece.dto.PieceListResponse;
import com.moing.backend.domain.piece.dto.PiecePositionUpdateRequest;
import com.moing.backend.domain.piece.dto.PiecePositionUpdateResponse;
import com.moing.backend.domain.piece.dto.PieceUpdateRequest;
import com.moing.backend.domain.piece.service.PieceService;
import com.moing.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 조각 API.
 *
 * <p>조각 자체는 /api/pieces, 보드(사람에 매달린 목록·배치)는 /api/users/... 아래라
 * 경로가 둘로 갈린다. 한 기능이므로 컨트롤러는 하나로 둔다.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PieceController {

    private final PieceService pieceService;

    @PostMapping("/pieces")
    public ResponseEntity<ApiResponse<PieceCreateResponse>> createPiece(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PieceCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("success", pieceService.createPiece(userId, request)));
    }

    @GetMapping("/users/me/pieces")
    public ResponseEntity<ApiResponse<PieceListResponse>> getMyPieces(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success("success", pieceService.getMyPieces(userId)));
    }

    @PatchMapping("/pieces/{id}")
    public ResponseEntity<Void> updatePiece(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody PieceUpdateRequest request
    ) {
        pieceService.updatePiece(userId, id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/pieces/{id}")
    public ResponseEntity<Void> deletePiece(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        pieceService.deletePiece(userId, id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/users/me/pieces/positions")
    public ResponseEntity<ApiResponse<PiecePositionUpdateResponse>> updatePiecePositions(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PiecePositionUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("success",
                pieceService.updatePositions(userId, request)));
    }

    // "/users/me/pieces"는 리터럴이라 "/users/{userId}/pieces"보다 먼저 매칭된다
    @GetMapping("/users/{userId}/pieces")
    public ResponseEntity<ApiResponse<PieceListResponse>> getUserPieces(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success("success",
                pieceService.getUserPieces(viewerId, userId)));
    }
}
