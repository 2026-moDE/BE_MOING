package com.moing.backend.domain.follow.repository;

import com.moing.backend.domain.follow.entity.Follow;
import com.moing.backend.domain.follow.entity.FollowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    boolean existsByFollowerIdAndFollowingIdAndStatusIn(Long followerId, Long followingId, List<FollowStatus> statuses);

    Optional<Follow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    List<Follow> findByFollowingIdAndStatus(Long followingId, FollowStatus status);

    List<Follow> findByFollowerIdAndStatus(Long followerId, FollowStatus status);

    List<Follow> findByFollowerIdAndFollowingIdIn(Long followerId, List<Long> followingIds);
}
