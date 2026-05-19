package com.moing.backend.domain.review.repository;

import com.moing.backend.domain.review.entity.ReviewHelpful;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface ReviewHelpfulRepository extends JpaRepository<ReviewHelpful, Long> {

    @Query("SELECT rh.reviewId FROM ReviewHelpful rh WHERE rh.userId = :userId AND rh.reviewId IN :reviewIds")
    Set<Long> findHelpfulReviewIds(@Param("userId") Long userId, @Param("reviewIds") List<Long> reviewIds);
}
