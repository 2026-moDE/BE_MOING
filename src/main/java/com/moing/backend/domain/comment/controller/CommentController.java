package com.moing.backend.domain.comment.controller;

import com.moing.backend.domain.comment.dto.CommentCreateRequest;
import com.moing.backend.domain.comment.dto.CommentCreateResponse;
import com.moing.backend.domain.comment.dto.CommentListResponse;
import com.moing.backend.domain.comment.dto.ReplyCreateRequest;
import com.moing.backend.domain.comment.service.CommentService;
import com.moing.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews/{id}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    public ResponseEntity<ApiResponse<CommentCreateResponse>> createComment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody CommentCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("success", commentService.createComment(userId, id, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CommentListResponse>> getComments(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success("success",
                commentService.getComments(userId, id, cursor, limit)));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @PathVariable Long commentId
    ) {
        commentService.deleteComment(userId, id, commentId);
        return ResponseEntity.ok(ApiResponse.success("댓글이 삭제되었습니다", null));
    }

    @PostMapping("/{commentId}/replies")
    public ResponseEntity<ApiResponse<CommentCreateResponse>> createReply(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @PathVariable Long commentId,
            @Valid @RequestBody ReplyCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("success",
                        commentService.createReply(userId, id, commentId, request)));
    }
}
