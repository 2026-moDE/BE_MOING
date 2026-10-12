package com.moing.backend.domain.magazine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매거진 묶음에 담긴 리뷰 한 장.
 *
 * <p>다른 엔티티와 같이 연관관계 없이 id만 들고 있다. 그래서 JPA가 외래키를 만들지 않으므로,
 * 묶음이 지워질 때 아이템이 함께 지워지게 하려면 운영 DB에 직접 걸어야 한다
 * (배치는 아이템을 먼저 지우고 묶음을 지우므로 제약이 없어도 고아 행은 남지 않는다):
 *   ALTER TABLE magazine_items
 *     ADD CONSTRAINT fk_magazine_items_magazine
 *     FOREIGN KEY (magazine_id) REFERENCES magazines(id) ON DELETE CASCADE;
 */
@Entity
@Table(name = "magazine_items", indexes = {
        @Index(name = "idx_magazine_items_magazine_id", columnList = "magazine_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MagazineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "magazine_id", nullable = false)
    private Long magazineId;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    /** 묶음 안에서의 노출 순서. 0부터 */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    private MagazineItem(Long magazineId, Long reviewId, int sortOrder) {
        this.magazineId = magazineId;
        this.reviewId = reviewId;
        this.sortOrder = sortOrder;
    }

    public static MagazineItem of(Long magazineId, Long reviewId, int sortOrder) {
        return new MagazineItem(magazineId, reviewId, sortOrder);
    }
}
