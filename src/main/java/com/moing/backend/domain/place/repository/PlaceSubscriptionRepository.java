package com.moing.backend.domain.place.repository;

import com.moing.backend.domain.place.entity.PlaceSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceSubscriptionRepository extends JpaRepository<PlaceSubscription, Long> {

    boolean existsByUserIdAndPlaceId(Long userId, Long placeId);

    long countByUserId(Long userId);

    java.util.List<PlaceSubscription> findByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByUserIdAndPlaceId(Long userId, Long placeId);
}
