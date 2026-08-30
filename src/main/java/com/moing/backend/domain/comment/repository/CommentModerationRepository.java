package com.moing.backend.domain.comment.repository;

import com.moing.backend.domain.comment.entity.CommentModeration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentModerationRepository extends JpaRepository<CommentModeration, Long> {

    List<CommentModeration> findByCommentIdIn(List<Long> commentIds);

    boolean existsByCommentId(Long commentId);
}
