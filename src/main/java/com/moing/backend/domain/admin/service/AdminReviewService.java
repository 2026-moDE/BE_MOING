package com.moing.backend.domain.admin.service;

import com.moing.backend.domain.admin.dto.AdminReviewListResponse;
import com.moing.backend.domain.comment.repository.CommentRepository;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.place.service.CongestionCacheService;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReviewService {

    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "ARCHIVED", "BLINDED");

    private final ReviewRepository reviewRepository;
    private final CongestionCacheService congestionCacheService;
    private final CommentRepository commentRepository;

    public AdminReviewListResponse getReviews(String status, Long cursor, Integer limit) {
        if (limit == null) limit = 20;
        limit = Math.min(limit, 100);

        String filterStatus = null;
        if (status != null) {
            filterStatus = status.toUpperCase();
            if (!VALID_STATUSES.contains(filterStatus)) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
        }

        List<Object[]> rows = reviewRepository.findAdminReviews(
                filterStatus, cursor, PageRequest.of(0, limit + 1));

        boolean hasNext = rows.size() > limit;
        List<Object[]> page = hasNext ? rows.subList(0, limit) : rows;
        Long nextCursor = hasNext ? ((Number) page.get(page.size() - 1)[0]).longValue() : null;

        Map<Long, Long> commentCountMap = commentRepository.countMapByReviewIds(
                page.stream().map(row -> ((Number) row[0]).longValue()).toList());

        List<AdminReviewListResponse.ReviewItem> items = page.stream().map(row -> {
            Long reviewId = ((Number) row[0]).longValue();
            boolean isBlinded = Boolean.TRUE.equals(row[6]);
            String dbStatus = (String) row[7];
            String derivedStatus = isBlinded ? "BLINDED" : dbStatus;
            String nickname = row[5] != null ? (String) row[5] : "(탈퇴한 사용자)";

            return new AdminReviewListResponse.ReviewItem(
                    reviewId,
                    (String) row[1],       // place_name (nullable)
                    (String) row[2],       // image_url
                    (String) row[9],       // thumbnail_url
                    (String) row[10],      // thumbnail_small_url
                    (String) row[3],       // congestion_level
                    (String) row[4],       // comment
                    nickname,
                    derivedStatus,
                    commentCountMap.getOrDefault(reviewId, 0L),
                    (LocalDateTime) row[8] // created_at
            );
        }).toList();

        return new AdminReviewListResponse(items, nextCursor);
    }

    @Transactional
    public void blindReview(Long reviewId, String status) {
        String upper = status.toUpperCase();
        if (!"BLINDED".equals(upper) && !"ACTIVE".equals(upper)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        if ("BLINDED".equals(upper)) {
            review.blind();
        } else {
            review.unblind();
        }

        // 블라인드 여부가 혼잡도 집계 대상을 바꾸므로 즉시 갱신한다
        congestionCacheService.refreshForPlace(review.getPlaceId());
    }
}
