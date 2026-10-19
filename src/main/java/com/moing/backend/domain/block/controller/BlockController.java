package com.moing.backend.domain.block.controller;

import com.moing.backend.domain.block.dto.BlockListResponse;
import com.moing.backend.domain.block.service.BlockService;
import com.moing.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    /**
     * 사용자 차단.
     *
     * <p>이미 차단한 상대여도 200이다(멱등). 차단과 함께 친구 관계와 양쪽 친구 요청이 정리된다.
     */
    @PostMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<Void>> block(
            @AuthenticationPrincipal Long myId,
            @PathVariable Long userId
    ) {
        blockService.block(myId, userId);
        return ResponseEntity.ok(ApiResponse.success("success", null));
    }

    /**
     * 차단 해제.
     *
     * <p>차단 상태가 아니어도 200이다(멱등). 친구 관계는 복구되지 않는다.
     */
    @DeleteMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<Void>> unblock(
            @AuthenticationPrincipal Long myId,
            @PathVariable Long userId
    ) {
        blockService.unblock(myId, userId);
        return ResponseEntity.ok(ApiResponse.success("success", null));
    }

    /** 내가 차단한 사용자 목록 (최근 차단순, 페이지네이션 없음) */
    @GetMapping("/me/blocks")
    public ResponseEntity<ApiResponse<BlockListResponse>> getBlocks(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success("success", blockService.getBlocks(userId)));
    }
}
