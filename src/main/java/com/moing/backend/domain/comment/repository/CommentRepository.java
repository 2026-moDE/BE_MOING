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

    // 답글 존재 여부 (삭제 방식 결정용, 소프트 삭제된 답글도 부모를 붙들고 있으므로 포함한다)
    boolean existsByParentId(Long parentId);

    // 관리자 댓글 목록 (author_nickname, place_name 조인, 삭제된 댓글 포함)
    // users를 네이티브로 조인해야 탈퇴 유저(@SQLRestriction 대상)의 닉네임도 남는다
    @Query(value = """
            SELECT c.id, c.review_id, c.parent_id, c.content,
                   u.nickname AS author_nickname, p.name AS place_name,
                   c.is_secret, c.is_deleted, c.created_at
            FROM comments c
            LEFT JOIN users u ON u.id = c.user_id
            LEFT JOIN reviews r ON r.id = c.review_id
            LEFT JOIN places p ON p.id = r.place_id
            WHERE (:cursor IS NULL OR c.id < :cursor)
              AND (:keyword IS NULL OR c.content LIKE CONCAT('%', :keyword, '%'))
              AND (:filterStatus IS NULL
                   OR (:filterStatus = 'DELETED' AND c.is_deleted = true)
                   OR (:filterStatus = 'ACTIVE' AND c.is_deleted = false))
            ORDER BY c.id DESC
            """, nativeQuery = true)
    List<Object[]> findAdminComments(@Param("filterStatus") String filterStatus,
                                     @Param("keyword") String keyword,
                                     @Param("cursor") Long cursor,
                                     Pageable pageable);
}
