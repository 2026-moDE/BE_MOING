package com.moing.backend.domain.follow.repository;

import com.moing.backend.domain.follow.entity.Follow;
import com.moing.backend.domain.follow.entity.FollowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 두 사용자 사이의 친구 관계와 친구 요청을 양방향으로 모두 지운다.
     *
     * <p>차단할 때 쓴다. 친구 관계는 ACCEPTED 두 행으로 표현되고 요청은 PENDING/REJECTED
     * 한 행이라, 방향을 가리지 않고 지워야 "차단했는데 친구 목록에 남아 있는" 상태나
     * 차단한 상대의 요청이 알림함에 남는 상태가 생기지 않는다.
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Follow f " +
            "WHERE (f.followerId = :userId AND f.followingId = :otherUserId) " +
            "   OR (f.followerId = :otherUserId AND f.followingId = :userId)")
    int deleteBetween(@Param("userId") Long userId, @Param("otherUserId") Long otherUserId);

    // 탈퇴한 유저(@SQLRestriction으로 제외됨)는 친구 수에서 빠지도록 User와 조인해 카운트
    @Query("select count(f) from Follow f join User u on u.id = f.followingId " +
            "where f.followerId = :userId and f.status = com.moing.backend.domain.follow.entity.FollowStatus.ACCEPTED")
    long countFriends(@Param("userId") Long userId);
}
