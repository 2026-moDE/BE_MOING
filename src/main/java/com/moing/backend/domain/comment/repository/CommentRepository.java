package com.moing.backend.domain.comment.repository;

import com.moing.backend.domain.comment.entity.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 최상위 댓글 커서 기반 조회 (오래된 순)
    @Query("""
            SELECT c FROM Comment c
            WHERE c.reviewId = :reviewId
              AND c.parentId IS NULL
              AND (:cursor IS NULL OR c.id > :cursor)
            ORDER BY c.id ASC
            """)
    List<Comment> findTopLevelComments(
            @Param("reviewId") Long reviewId,
            @Param("cursor") Long cursor,
            Pageable pageable);

    // 조회된 댓글들의 답글 일괄 조회 (N+1 방지)
    @Query("""
            SELECT c FROM Comment c
            WHERE c.parentId IN :parentIds
            ORDER BY c.id ASC
            """)
    List<Comment> findRepliesByParentIds(@Param("parentIds") List<Long> parentIds);

    // 답글 존재 여부 (삭제 방식 결정용, FK 제약 때문에 소프트 삭제된 답글도 포함해야 한다)
    boolean existsByParentId(Long parentId);
}
