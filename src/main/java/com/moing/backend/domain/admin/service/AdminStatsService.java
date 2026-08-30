package com.moing.backend.domain.admin.service;

import com.moing.backend.domain.admin.dto.AdminStatsResponse;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.review.entity.ReportStatus;
import com.moing.backend.domain.review.repository.ReviewReportRepository;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminStatsService {

    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewReportRepository reviewReportRepository;
    private final PlaceRepository placeRepository;

    public AdminStatsResponse getStats() {
        long totalUsers = userRepository.countByDeletedAtIsNull();

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        long todayReviews = reviewRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end);

        long pendingReports = reviewReportRepository.countByStatus(ReportStatus.PENDING);

        long activePlaces = placeRepository.countByIsActiveTrue();

        return new AdminStatsResponse(totalUsers, todayReviews, pendingReports, activePlaces);
    }
}
