package com.moing.backend.domain.piece.entity;

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
 * 마이페이지 보드에 올리는 조각.
 *
 * <p>리뷰 사진에서 누끼를 딴 투명 PNG 한 장이 조각 하나이고, 리뷰 1건당 조각은 하나만
 * 만들 수 있다(review_id unique).
 *
 * <p>position_x/y는 보드 너비·높이에 대한 비율(0~1)이라 화면 크기가 달라도 같은 자리에
 * 놓인다. 아직 보드에 올리지 않은 조각은 둘 다 null이다. rotation(기울기)과 scale(배율)은
 * 미배치 상태에도 기본값을 갖는다.
 *
 * <p>목록에 내려가는 created_at은 이 엔티티의 created_at(조각을 만든 시각)이 아니라
 * 원본 리뷰의 created_at이다. 사용자에게 조각은 "그때 다녀온 기록"이기 때문이다.
 */
@Entity
@Table(name = "pieces",
        uniqueConstraints = @UniqueConstraint(name = "uk_pieces_review_id", columnNames = "review_id"),
        indexes = @Index(name = "idx_pieces_user_id", columnList = "user_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Piece {

    /** 배율 기본값. 등록 직후와 scale 도입 이전 조각이 모두 이 값이다 */
    public static final BigDecimal DEFAULT_SCALE = new BigDecimal("1.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "name", length = 30)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private PieceVisibility visibility;

    @Column(name = "position_x", precision = 5, scale = 4)
    private BigDecimal positionX;

    @Column(name = "position_y", precision = 5, scale = 4)
    private BigDecimal positionY;

    @Column(name = "rotation", nullable = false)
    private short rotation;

    // 컬럼 default를 DDL에 박아둬야 scale 추가 이전에 쌓인 조각도 1.00으로 채워진다
    // (NOT NULL 컬럼을 default 없이 추가하면 기존 행 때문에 alter가 실패한다)
    @Column(name = "scale", nullable = false, precision = 3, scale = 2,
            columnDefinition = "numeric(3,2) default 1.00")
    private BigDecimal scale;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Piece(Long userId, Long reviewId, String imageUrl, String name, PieceVisibility visibility) {
        this.userId = userId;
        this.reviewId = reviewId;
        this.imageUrl = imageUrl;
        this.name = name;
        this.visibility = visibility != null ? visibility : PieceVisibility.PUBLIC;
        this.rotation = 0;
        this.scale = DEFAULT_SCALE;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateVisibility(PieceVisibility visibility) {
        this.visibility = visibility;
    }

    /** 보드 위 위치·기울기·배율을 덮어쓴다. 값 검증은 PieceService가 한다. */
    public void place(BigDecimal positionX, BigDecimal positionY, short rotation, BigDecimal scale) {
        this.positionX = positionX;
        this.positionY = positionY;
        this.rotation = rotation;
        this.scale = scale;
    }
}
