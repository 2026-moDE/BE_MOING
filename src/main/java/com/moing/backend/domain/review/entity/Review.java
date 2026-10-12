package com.moing.backend.domain.review.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 리뷰 엔티티.
 *
 * <p>좌표 인덱스는 탐색 탭의 반경 조회가 쓴다. 하버사인 식은 인덱스를 탈 수 없어
 * 쿼리가 먼저 좌표 범위(바운딩 박스)로 후보를 좁히는데, 그 범위 조건이 이 인덱스를 탄다.
 * 인덱스가 없으면 전체 리뷰에 삼각함수가 돌아간다.
 */
@Entity
@Table(name = "reviews", indexes = {
        @Index(name = "idx_reviews_latitude_longitude", columnList = "latitude, longitude")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "congestion_level", length = 20)
    private CongestionLevel congestionLevel;

    @Column(name = "quick_tag", length = 50)
    private String quickTag;

    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "thumbnail_small_url", length = 500)
    private String thumbnailSmallUrl;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", length = 20)
    private Visibility visibility;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "is_blinded", nullable = false)
    private boolean isBlinded;

    @Column(name = "helpful_count", nullable = false)
    private int helpfulCount;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Review(Long userId, Long placeId, CongestionLevel congestionLevel,
                  String quickTag, String comment, String imageUrl,
                  BigDecimal latitude, BigDecimal longitude, Visibility visibility) {
        this.userId = userId;
        this.placeId = placeId;
        this.congestionLevel = congestionLevel;
        this.quickTag = quickTag;
        this.comment = comment;
        this.imageUrl = imageUrl;
        this.thumbnailUrl = generateThumbnailUrl(imageUrl, "thumbnail");
        this.thumbnailSmallUrl = generateThumbnailUrl(imageUrl, "thumbnail_small");
        this.latitude = latitude;
        this.longitude = longitude;
        this.visibility = visibility != null ? visibility : Visibility.PUBLIC;
        this.status = "ACTIVE";
        this.isBlinded = false;
        this.helpfulCount = 0;
        this.viewCount = 0;
    }

    private static String generateThumbnailUrl(String imageUrl, String folder) {
        if (imageUrl == null || !imageUrl.contains("/original/")) {
            return null;
        }
        return imageUrl.replace("/original/", "/" + folder + "/");
    }

    public void blind() {
        this.isBlinded = true;
    }

    public void unblind() {
        this.isBlinded = false;
    }

    public void updateComment(String comment) {
        this.comment = comment;
    }
}
