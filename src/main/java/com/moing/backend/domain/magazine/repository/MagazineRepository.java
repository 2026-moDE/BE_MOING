package com.moing.backend.domain.magazine.repository;

import com.moing.backend.domain.magazine.entity.Magazine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MagazineRepository extends JpaRepository<Magazine, Long> {

    /**
     * 노출 후보 전체. 규칙이 다섯 개뿐이라 묶음 수가 한 자리라서 전부 읽고 서비스에서 고른다
     * (노출 조건·아이템 수 판단에 아이템과 리뷰가 모두 필요해 쿼리로는 한 번에 못 끝낸다).
     */
    List<Magazine> findAllByOrderByPriorityDescIdAsc();

    /**
     * 배치 중복 실행을 막는 DB 락을 건다 (PostgreSQL 어드바이저리 락).
     *
     * <p>{@code @Scheduled}는 인스턴스마다 돌아서, 서버가 여러 대면 같은 시각에 모두 깨어나
     * 전체 삭제 후 재생성을 동시에 수행한다. 그러면 묶음이 중복 생성되거나 한쪽이 지운 뒤
     * 다른 쪽이 넣는 상태가 된다.
     *
     * <p>{@code xact} 변종이라 <b>호출한 트랜잭션이 끝나면 자동으로 풀린다</b>. 인스턴스가
     * 도중에 죽어도 락이 남지 않아 별도 테이블이나 만료 처리가 필요 없다. 이미 누군가
     * 들고 있으면 기다리지 않고 바로 {@code false}를 돌려준다.
     *
     * <p>락 키는 DB 전체에서 공유되는 숫자다. 다른 배치에 쓸 때는 키를 겹치지 않게 정해야 한다.
     */
    @Query(value = "SELECT pg_try_advisory_xact_lock(:key)", nativeQuery = true)
    boolean tryAdvisoryLock(@Param("key") long key);
}
