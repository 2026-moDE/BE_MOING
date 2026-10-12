package com.moing.backend.domain.magazine.repository;

import com.moing.backend.domain.magazine.entity.MagazineItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MagazineItemRepository extends JpaRepository<MagazineItem, Long> {

    /** 목록 조회: 여러 묶음의 아이템을 한 번에 (묶음별 sort_order 순) */
    List<MagazineItem> findByMagazineIdInOrderByMagazineIdAscSortOrderAsc(List<Long> magazineIds);

    /** 상세 조회: 한 묶음의 아이템 (sort_order 순) */
    List<MagazineItem> findByMagazineIdOrderBySortOrderAsc(Long magazineId);
}
