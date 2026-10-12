package com.moing.backend.domain.magazine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 홈 바텀시트 매거진 묶음.
 *
 * <p>하루 1회 배치가 전체를 지우고 다시 만든다. 조회는 읽기만 한다.
 * 제목은 {@link MagazineRule}의 고정 문구를 복사해 둔다 — 규칙 문구를 고쳐도
 * 이미 만들어진 묶음의 제목이 따라 바뀌지 않아야 응답이 배치 시점과 일치한다.
 */
@Entity
@Table(name = "magazines")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Magazine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    /** 노출 조건 판단에 쓴다 (비 올 때만, 저녁 이후에만 등) */
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_key", nullable = false, length = 50)
    private MagazineRule ruleKey;

    @Column(nullable = false)
    private int priority;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Magazine(String title, MagazineRule ruleKey, int priority) {
        this.title = title;
        this.ruleKey = ruleKey;
        this.priority = priority;
    }

    public static Magazine of(MagazineRule rule) {
        return new Magazine(rule.getHeadline(), rule, rule.getPriority());
    }
}
