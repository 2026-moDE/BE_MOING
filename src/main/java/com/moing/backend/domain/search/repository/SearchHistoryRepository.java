package com.moing.backend.domain.search.repository;

import com.moing.backend.domain.search.entity.SearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {
}
