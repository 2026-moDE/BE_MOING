package com.moing.backend.domain.comment.repository;

import com.moing.backend.domain.comment.entity.CommentModeration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentModerationRepository extends JpaRepository<CommentModeration, Long> {

    List<CommentModeration> findByCommentIdIn(List<Long> commentIds);

    boolean existsByCommentId(Long commentId);

    // 댓글이 사라지면 그 검열 결과도 참조할 곳이 없어지므로 함께 지운다
    @Modifying
    @Query("DELETE FROM CommentModeration m WHERE m.commentId IN :commentIds")
    int deleteAllByCommentIdIn(@Param("commentIds") List<Long> commentIds);
}
