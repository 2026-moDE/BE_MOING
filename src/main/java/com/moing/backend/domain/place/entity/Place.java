package com.moing.backend.domain.place.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 장소 엔티티
 * 카페, 팝업 등 다양한 카테고리의 장소 정보를 관리한다.
 *
 * 활성 장소의 name은 유일해야 한다. 비활성 장소는 같은 이름이 남아 있을 수 있으므로
 * 전체 unique 제약 대신 부분 unique 인덱스로 강제한다 (JPA로는 표현 불가).
 *   CREATE UNIQUE INDEX uk_places_name_active ON places (name) WHERE is_active = true;
 * 이 인덱스가 없으면 동시 검색 요청이 같은 장소를 중복 삽입하고,
 * 이후 이름 조회가 전부 실패한다.
 */
@Entity
@Table(name = "places")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 300)
    private String address;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PlaceCategory category;

    @Column(name = "business_hours", length = 200)
    private String businessHours;

    @Column(length = 20)
    private String source;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void updateIsActive(boolean isActive) {
        this.isActive = isActive;
    }

    public void updateCategory(PlaceCategory category) {
        this.category = category;
    }
}
