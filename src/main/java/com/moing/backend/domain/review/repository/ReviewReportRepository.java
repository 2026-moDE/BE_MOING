package com.moing.backend.domain.review.repository;

import com.moing.backend.domain.review.entity.ReportStatus;
import com.moing.backend.domain.review.entity.ReviewReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // 리뷰 삭제 시 딸린 신고 정리. 대상 리뷰가 사라지면 관리자 화면에서도 다룰 수 없는 행이 된다
    @Modifying
    @Query("DELETE FROM ReviewReport r WHERE r.reviewId = :reviewId")
    int deleteAllByReviewId(@Param("reviewId") Long reviewId);
}
