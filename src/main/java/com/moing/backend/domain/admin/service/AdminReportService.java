package com.moing.backend.domain.admin.service;

import com.moing.backend.domain.admin.dto.AdminReportListResponse;
import com.moing.backend.domain.review.entity.ReportStatus;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.entity.ReviewReport;
import com.moing.backend.domain.review.repository.ReviewReportRepository;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.domain.user.entity.User;
import com.moing.backend.domain.user.repository.UserRepository;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportService {

    private final ReviewReportRepository reviewReportRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    public AdminReportListResponse getReports(String status, Long cursor, Integer limit) {
        if (limit == null) limit = 20;
        limit = Math.min(limit, 100);

        ReportStatus reportStatus = null;
        if (status != null) {
            try {
                reportStatus = ReportStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
        }

        List<ReviewReport> reports = reviewReportRepository.findReports(
                reportStatus, cursor, PageRequest.of(0, limit + 1));

        boolean hasNext = reports.size() > limit;
        List<ReviewReport> page = hasNext ? reports.subList(0, limit) : reports;
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        // Review 일괄 조회
        List<Long> reviewIds = page.stream().map(ReviewReport::getReviewId).distinct().toList();
        Map<Long, Review> reviewMap = reviewRepository.findAllById(reviewIds).stream()
                .collect(Collectors.toMap(Review::getId, Function.identity()));

        // Reporter(User) 일괄 조회
        List<Long> reporterIds = page.stream().map(ReviewReport::getReporterId).distinct().toList();
        Map<Long, User> userMap = userRepository.findAllById(reporterIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<AdminReportListResponse.ReportItem> items = page.stream().map(r -> {
            Review review = reviewMap.get(r.getReviewId());
            String imageUrl = review != null ? review.getImageUrl() : null;

            User reporter = userMap.get(r.getReporterId());
            String nickname = reporter != null ? reporter.getNickname() : "(탈퇴한 사용자)";

            return new AdminReportListResponse.ReportItem(
                    r.getId(),
                    r.getReviewId(),
                    imageUrl,
                    r.getReason(),
                    r.getDetail(),
                    nickname,
                    r.getStatus().name(),
                    r.getCreatedAt()
            );
        }).toList();

        return new AdminReportListResponse(items, nextCursor);
    }

    private static final Set<ReportStatus> ALLOWED_STATUSES = Set.of(ReportStatus.RESOLVED, ReportStatus.REJECTED);

    @Transactional
    public void processReport(Long reportId, String status, Long adminId) {
        ReportStatus reportStatus;
        try {
            reportStatus = ReportStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        if (!ALLOWED_STATUSES.contains(reportStatus)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        ReviewReport report = reviewReportRepository.findById(reportId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        if (reportStatus == ReportStatus.REJECTED) {
            report.process(ReportStatus.REJECTED, adminId);
        } else {
            // RESOLVED: 같은 review_id의 PENDING 신고를 전부 처리
            List<ReviewReport> pendingReports = reviewReportRepository
                    .findByReviewIdAndStatus(report.getReviewId(), ReportStatus.PENDING);
            for (ReviewReport r : pendingReports) {
                r.process(ReportStatus.RESOLVED, adminId);
            }
            // 요청받은 건이 PENDING이 아니었더라도 RESOLVED로 변경
            report.process(ReportStatus.RESOLVED, adminId);

            reviewRepository.findById(report.getReviewId())
                    .ifPresent(Review::blind);
        }
    }
}
