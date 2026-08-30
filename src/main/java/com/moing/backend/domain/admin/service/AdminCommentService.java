package com.moing.backend.domain.admin.service;

import com.moing.backend.domain.admin.dto.AdminCommentListResponse;
import com.moing.backend.domain.comment.entity.Comment;
import com.moing.backend.domain.comment.repository.CommentRepository;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCommentService {

    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "DELETED");

    private final CommentRepository commentRepository;

    public AdminCommentListResponse getComments(String status, String keyword, Long cursor, Integer limit) {
        if (limit == null) limit = 20;
        limit = Math.min(limit, 100);

        String filterStatus = null;
        if (status != null) {
            filterStatus = status.toUpperCase();
            if (!VALID_STATUSES.contains(filterStatus)) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
        }

        String filterKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        List<Object[]> rows = commentRepository.findAdminComments(
                filterStatus, filterKeyword, cursor, PageRequest.of(0, limit + 1));

        boolean hasNext = rows.size() > limit;
        List<Object[]> page = hasNext ? rows.subList(0, limit) : rows;
        Long nextCursor = hasNext ? ((Number) page.get(page.size() - 1)[0]).longValue() : null;

        List<AdminCommentListResponse.CommentItem> items = page.stream().map(row -> {
            String nickname = row[4] != null ? (String) row[4] : "(탈퇴한 사용자)";
            boolean isDeleted = Boolean.TRUE.equals(row[7]);

            return new AdminCommentListResponse.CommentItem(
                    ((Number) row[0]).longValue(),
                    ((Number) row[1]).longValue(),  // review_id
                    (String) row[5],                // place_name (nullable)
                    (String) row[3],                // content
                    nickname,
                    Boolean.TRUE.equals(row[6]),    // is_secret
                    row[2] != null,                 // parent_id가 있으면 답글
                    isDeleted ? "DELETED" : "ACTIVE",
                    (LocalDateTime) row[8]          // created_at
            );
        }).toList();

        return new AdminCommentListResponse(items, nextCursor);
    }

    // 댓글 내리기 (되돌릴 수 없다)
    @Transactional
    public void deleteComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));

        // 이미 내려간 댓글에 다시 요청해도 에러로 만들지 않는다
        if (comment.isDeleted()) {
            return;
        }

        // 유저가 지울 때와 같은 규칙을 쓴다.
        // 답글이 달린 댓글을 실제로 지우면 그 답글의 parent_id가 없는 id를 가리키게 되고,
        // 최상위 조회에도 답글 조회에도 걸리지 않아 아무 화면에 안 나오는 유령으로 남는다.
        if (commentRepository.existsByParentId(comment.getId())) {
            comment.softDelete();
        } else {
            commentRepository.delete(comment);
        }
    }
}
