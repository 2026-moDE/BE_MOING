package com.moing.backend.domain.search.repository;

import com.moing.backend.domain.search.entity.SearchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    @Query("""
            SELECT s FROM SearchHistory s
            WHERE s.userId = :userId
              AND s.createdAt = (
                  SELECT MAX(s2.createdAt) FROM SearchHistory s2
                  WHERE s2.userId = s.userId AND s2.keyword = s.keyword
              )
            ORDER BY s.createdAt DESC
            """)
    List<SearchHistory> findRecentByUserId(@Param("userId") Long userId, Pageable pageable);

    void deleteAllByUserId(Long userId);

    void deleteAllByUserIdAndKeyword(Long userId, String keyword);
}
