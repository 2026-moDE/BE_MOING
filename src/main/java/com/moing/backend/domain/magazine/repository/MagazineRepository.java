package com.moing.backend.domain.magazine.repository;

import com.moing.backend.domain.magazine.entity.Magazine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MagazineRepository extends JpaRepository<Magazine, Long> {

    /**
     * 노출 후보 전체. 규칙이 다섯 개뿐이라 묶음 수가 한 자리라서 전부 읽고 서비스에서 고른다
     * (노출 조건·아이템 수 판단에 아이템과 리뷰가 모두 필요해 쿼리로는 한 번에 못 끝낸다).
     */
    List<Magazine> findAllByOrderByPriorityDescIdAsc();
}
