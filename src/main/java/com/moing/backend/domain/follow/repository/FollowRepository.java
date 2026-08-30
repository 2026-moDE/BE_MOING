package com.moing.backend.domain.follow.repository;

import com.moing.backend.domain.follow.entity.Follow;
import com.moing.backend.domain.follow.entity.FollowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    boolean existsByFollowerIdAndFollowingIdAndStatusIn(Long followerId, Long followingId, List<FollowStatus> statuses);

    Optional<Follow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    List<Follow> findByFollowingIdAndStatus(Long followingId, FollowStatus status);

    List<Follow> findByFollowerIdAndStatus(Long followerId, FollowStatus status);

    List<Follow> findByFollowerIdAndFollowingIdIn(Long followerId, List<Long> followingIds);

    // 목록 조회에서 친구 여부를 한 번에 판정하기 위한 배치 조회 (작성자별 exists N+1 방지)
    @Query("select f.followingId from Follow f " +
            "where f.followerId = :followerId and f.followingId in :followingIds " +
            "and f.status = com.moing.backend.domain.follow.entity.FollowStatus.ACCEPTED")
    List<Long> findAcceptedFollowingIds(@Param("followerId") Long followerId,
                                        @Param("followingIds") List<Long> followingIds);

    // 탈퇴한 유저(@SQLRestriction으로 제외됨)는 친구 수에서 빠지도록 User와 조인해 카운트
    @Query("select count(f) from Follow f join User u on u.id = f.followingId " +
            "where f.followerId = :userId and f.status = com.moing.backend.domain.follow.entity.FollowStatus.ACCEPTED")
    long countFriends(@Param("userId") Long userId);
}
