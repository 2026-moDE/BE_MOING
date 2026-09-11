package com.moing.backend.domain.place.service;

import com.moing.backend.domain.place.entity.PlaceCongestionCache;
import com.moing.backend.domain.place.repository.PlaceCongestionCacheRepository;
import com.moing.backend.domain.review.entity.CongestionLevel;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CongestionCacheService {

    static final int WINDOW_HOURS = 3;
    static final int MAX_REVIEWS = 50;

    private final ReviewRepository reviewRepository;
    private final PlaceCongestionCacheRepository congestionCacheRepository;

    /** 리뷰 작성 직후 해당 장소의 혼잡도 캐시를 즉시 갱신한다. */
    @Transactional
    public void refreshForPlace(Long placeId) {
        LocalDateTime since = LocalDateTime.now().minusHours(WINDOW_HOURS);
        LocalDateTime now = LocalDateTime.now();

        List<Review> reviews = reviewRepository.findRecentActiveReviewsByPlaceId(
                placeId, since, PageRequest.of(0, MAX_REVIEWS));

        if (reviews.isEmpty()) {
            congestionCacheRepository.deleteById(placeId);
            return;
        }

        double index = calculateWeightedAverage(reviews, now);
        congestionCacheRepository.save(
                new PlaceCongestionCache(placeId, toCongestionLevel(index), index, reviews.size(), now));
    }

    double calculateWeightedAverage(List<Review> reviews, LocalDateTime now) {
        double weightedSum = 0;
        double weightSum = 0;

        for (Review review : reviews) {
            long elapsedMinutes = ChronoUnit.MINUTES.between(review.getCreatedAt(), now);
            double weight = 1.0 / (elapsedMinutes + 1);
            weightedSum += toScore(review.getCongestionLevel()) * weight;
            weightSum += weight;
        }

        return weightSum == 0 ? 0 : weightedSum / weightSum;
    }

    int toScore(CongestionLevel level) {
        return switch (level) {
            case RELAXED -> 1;
            case MODERATE -> 2;
            case CROWDED -> 3;
            case VERY_CROWDED -> 4;
        };
    }

    // 가중 평균 점수(1~4)를 4등분한 임계값으로 등급 분류
    CongestionLevel toCongestionLevel(double index) {
        if (index <= 1.75) return CongestionLevel.RELAXED;
        if (index <= 2.5) return CongestionLevel.MODERATE;
        if (index <= 3.25) return CongestionLevel.CROWDED;
        return CongestionLevel.VERY_CROWDED;
    }
}
