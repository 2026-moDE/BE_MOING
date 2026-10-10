package com.moing.backend.domain.congestion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 서울시 실시간 도시데이터 지역.
 *
 * <p>좌표에서 가장 가까운 지역을 찾기 위한 조회용 테이블로, 서울시가 제공하는 지역 목록을
 * 그대로 복제해 둔다. 내용은 {@code SeoulAreaSyncService}가 서울시에서 받아 채우므로
 * 코드에 지역명·좌표를 박아두지 않는다.
 *
 * <p>{@code areaNm}은 실시간 인구 API의 조회 키라 한 글자라도 다르면 데이터가 안 내려온다.
 * 서울시가 내려준 문자열을 가공 없이 저장해야 한다.
 */
@Entity
@Table(
        name = "seoul_areas",
        uniqueConstraints = @UniqueConstraint(name = "uk_seoul_areas_area_nm", columnNames = "area_nm")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeoulArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "area_nm", nullable = false, length = 100)
    private String areaNm;

    /** 서울시 분류 (관광특구 / 고궁·문화유산 / 공원 / 발달상권 / 인구밀집지역) */
    @Column(length = 30)
    private String category;

    @Column(precision = 10, scale = 7, nullable = false)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7, nullable = false)
    private BigDecimal longitude;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    private SeoulArea(String areaNm, String category, BigDecimal latitude, BigDecimal longitude, LocalDateTime syncedAt) {
        this.areaNm = areaNm;
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
        this.syncedAt = syncedAt;
    }

    public static SeoulArea of(String areaNm, String category, double latitude, double longitude, LocalDateTime syncedAt) {
        return new SeoulArea(areaNm, category, BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude), syncedAt);
    }

    /** 동기화 시 좌표·분류가 바뀌었을 수 있으므로 매번 덮어쓴다 */
    public void update(String category, double latitude, double longitude, LocalDateTime syncedAt) {
        this.category = category;
        this.latitude = BigDecimal.valueOf(latitude);
        this.longitude = BigDecimal.valueOf(longitude);
        this.syncedAt = syncedAt;
    }
}
