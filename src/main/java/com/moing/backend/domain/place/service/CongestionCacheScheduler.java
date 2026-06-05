package com.moing.backend.domain.place.service;

import com.moing.backend.domain.place.entity.PlaceCongestionCache;
import com.moing.backend.domain.place.repository.PlaceCongestionCacheRepository;
import com.moing.backend.domain.review.entity.CongestionLevel;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 5분마다 장소별 혼잡도를 계산해 place_congestion_cache 테이블에 저장한다.
 *
 * <p>계산 방식: 최근 3시간 ACTIVE 리뷰(최대 50건)에 시간 가중치(w = 1 / (경과분 + 1))를 적용한 가중 평균
 * <ul>
 *   <li>LOW(여유)  : 1.0 ~ 1.6</li>
 *   <li>MEDIUM(보통): 1.7 ~ 2.3</li>
 *   <li>HIGH(혼잡) : 2.4 ~ 3.0</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CongestionCacheScheduler {

    private static final int WINDOW_HOURS = 3;
    private static final int MAX_REVIEWS = 50;

    private final ReviewRepository reviewRepository;
    private final PlaceCongestionCacheRepository congestionCacheRepository;

    @Scheduled(fixedDelay = 5 * 60 * 1000) // 5분
    @Transactional
    public void refreshCongestionCache() {
        LocalDateTime since = LocalDateTime.now().minusHours(WINDOW_HOURS);

        // 최근 3시간 ACTIVE 리뷰 전체 조회
        List<Review> allReviews = reviewRepository.findAllRecentActiveReviews(since);

        // placeId별 그룹핑 후 최신 50개 제한
        Map<Long, List<Review>> grouped = allReviews.stream()
                .collect(Collectors.groupingBy(Review::getPlaceId));

        LocalDateTime now = LocalDateTime.now();

        List<PlaceCongestionCache> caches = grouped.entrySet().stream()
                .map(entry -> {
                    Long placeId = entry.getKey();
                    List<Review> reviews = entry.getValue().stream()
                            .sorted(Comparator.comparing(Review::getCreatedAt).reversed())
                            .limit(MAX_REVIEWS)
                            .toList();

                    double index = calculateWeightedAverage(reviews, now);
                    return new PlaceCongestionCache(placeId, toCongestionLevel(index), index, reviews.size(), now);
                })
                .toList();

        // upsert (기존 항목은 덮어쓰기, 새 항목은 삽입)
        congestionCacheRepository.saveAll(caches);

        // 3시간 동안 리뷰 없는 장소 캐시 삭제
        Set<Long> updatedIds = caches.stream()
                .map(PlaceCongestionCache::getPlaceId)
                .collect(Collectors.toSet());

        if (!updatedIds.isEmpty()) {
            congestionCacheRepository.deleteAllByPlaceIdNotIn(updatedIds);
        } else {
            congestionCacheRepository.deleteAll();
        }

        log.info("[CongestionCache] {}개 장소 갱신 완료", caches.size());
    }

    /** w = 1 / (경과분 + 1) 가중 평균 */
    private double calculateWeightedAverage(List<Review> reviews, LocalDateTime now) {
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

    private int toScore(CongestionLevel level) {
        return switch (level) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
        };
    }

    private CongestionLevel toCongestionLevel(double index) {
        if (index <= 1.6) return CongestionLevel.LOW;
        if (index <= 2.3) return CongestionLevel.MEDIUM;
        return CongestionLevel.HIGH;
    }
}
