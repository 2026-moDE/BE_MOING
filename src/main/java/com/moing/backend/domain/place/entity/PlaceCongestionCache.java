package com.moing.backend.domain.place.entity;

import com.moing.backend.domain.review.entity.CongestionLevel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "place_congestion_cache")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PlaceCongestionCache {

    @Id
    @Column(name = "place_id")
    private Long placeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "congestion_level", length = 20)
    private CongestionLevel congestionLevel;

    @Column(name = "congestion_index")
    private Double congestionIndex;

    // 최근 3시간 리뷰 수 (신뢰도 판단용)
    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
