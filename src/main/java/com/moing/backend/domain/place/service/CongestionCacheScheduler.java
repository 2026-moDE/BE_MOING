package com.moing.backend.domain.place.service;

import com.moing.backend.domain.place.entity.PlaceCongestionCache;
import com.moing.backend.domain.place.repository.PlaceCongestionCacheRepository;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
 *   <li>RELAXED(여유)        : 1.00 ~ 1.75</li>
 *   <li>MODERATE(보통)       : 1.75 ~ 2.50</li>
 *   <li>CROWDED(약간 붐빔)    : 2.50 ~ 3.25</li>
 *   <li>VERY_CROWDED(붐빔)   : 3.25 ~ 4.00</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CongestionCacheScheduler {

    private final ReviewRepository reviewRepository;
    private final PlaceCongestionCacheRepository congestionCacheRepository;
    private final CongestionCacheService congestionCacheService;

    @Scheduled(fixedDelay = 5 * 60 * 1000) // 5분
    @Transactional
    public void refreshCongestionCache() {
        LocalDateTime since = LocalDateTime.now().minusHours(CongestionCacheService.WINDOW_HOURS);

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
                            .limit(CongestionCacheService.MAX_REVIEWS)
                            .toList();

                    double index = congestionCacheService.calculateWeightedAverage(reviews, now);
                    return new PlaceCongestionCache(placeId, congestionCacheService.toCongestionLevel(index), index, reviews.size(), now);
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
}
