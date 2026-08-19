package com.moing.backend.domain.reaction.controller;

import com.moing.backend.domain.reaction.dto.ReactionCreateRequest;
import com.moing.backend.domain.reaction.dto.ReactionListResponse;
import com.moing.backend.domain.reaction.service.ReactionService;
import com.moing.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews/{id}/reactions")
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionService reactionService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> addReaction(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody ReactionCreateRequest request
    ) {
        reactionService.addReaction(userId, id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("이모지 반응이 추가되었습니다", null));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteReaction(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestParam String emoji
    ) {
        reactionService.deleteReaction(userId, id, emoji);
        return ResponseEntity.ok(ApiResponse.success("이모지 반응이 취소되었습니다", null));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ReactionListResponse>> getReactions(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success("success",
                reactionService.getReactions(userId, id)));
    }
}
