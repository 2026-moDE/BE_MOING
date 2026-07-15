package com.moing.backend.domain.review.repository;

import com.moing.backend.domain.review.entity.ReviewReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewReportRepository extends JpaRepository<ReviewReport, Long> {
    boolean existsByReviewIdAndReporterId(Long reviewId, Long reporterId);
}
