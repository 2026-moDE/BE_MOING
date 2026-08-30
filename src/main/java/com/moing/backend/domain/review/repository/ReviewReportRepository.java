package com.moing.backend.domain.review.repository;

import com.moing.backend.domain.review.entity.ReportStatus;
import com.moing.backend.domain.review.entity.ReviewReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ReviewReportRepository extends JpaRepository<ReviewReport, Long> {
    boolean existsByReviewIdAndReporterId(Long reviewId, Long reporterId);

    long countByStatus(ReportStatus status);

    List<ReviewReport> findByReviewIdAndStatus(Long reviewId, ReportStatus status);

    @Query("""
            SELECT r FROM ReviewReport r
            WHERE (:status IS NULL OR r.status = :status)
              AND (:cursor IS NULL OR r.id < :cursor)
            ORDER BY r.id DESC
            """)
    List<ReviewReport> findReports(
            @Param("status") ReportStatus status,
            @Param("cursor") Long cursor,
            Pageable pageable);
}
