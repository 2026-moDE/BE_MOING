package com.moing.backend.domain.comment.repository;

import com.moing.backend.domain.comment.entity.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // 리뷰에 달린 댓글 id 전체 (답글 포함). 검열 결과를 함께 지우기 위해 필요하다
    @Query("SELECT c.id FROM Comment c WHERE c.reviewId = :reviewId")
    List<Long> findIdsByReviewId(@Param("reviewId") Long reviewId);

    // 리뷰 삭제 시 딸린 댓글 정리.
    // 답글도 review_id를 갖고 있으므로 최상위 댓글과 답글이 한 번에 지워진다.
    // 리뷰 자체가 사라지는 상황이라 소프트 삭제(자리표시)는 의미가 없어 실제로 지운다.
    @Modifying
    @Query("DELETE FROM Comment c WHERE c.reviewId = :reviewId")
    int deleteAllByReviewId(@Param("reviewId") Long reviewId);

    // 아직 AI 검열을 거치지 않은 댓글 (오래된 것부터). 삭제된 댓글은 검사하지 않는다
    @Query(value = """
            SELECT c.id, c.content
            FROM comments c
            LEFT JOIN comment_moderations m ON m.comment_id = c.id
            WHERE m.id IS NULL
              AND c.is_deleted = false
            ORDER BY c.id ASC
            """, nativeQuery = true)
    List<Object[]> findUncheckedComments(Pageable pageable);

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
