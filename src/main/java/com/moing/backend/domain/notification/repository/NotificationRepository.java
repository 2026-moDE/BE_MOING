package com.moing.backend.domain.notification.repository;

import com.moing.backend.domain.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            SELECT n FROM Notification n
            WHERE n.userId = :userId
              AND (CAST(:cursor AS Long) IS NULL OR n.id < :cursor)
            ORDER BY n.id DESC
            """)
    List<Notification> findByUserId(
            @Param("userId") Long userId,
            @Param("cursor") Long cursor,
            Pageable pageable);

    /**
     * 알림함 마지막 방문 이후 도착한 알림이 있는지. 알림 아이콘의 점을 켜는 기준이다.
     *
     * <p>개별 읽음(is_read)이 아니라 방문 시각으로 판단한다. 알림함에 들어가면 점이 꺼지고,
     * 그 뒤 새 알림이 오면 다시 켜지는 동작이라 읽음 여부와는 무관하다.
     *
     * <p>한 번도 방문하지 않은 유저(checkedAt = null)는 알림이 하나라도 있으면 켠다.
     */
    @Query("""
            SELECT COUNT(n) > 0 FROM Notification n
            WHERE n.userId = :userId
              AND (CAST(:checkedAt AS LocalDateTime) IS NULL OR n.createdAt > :checkedAt)
            """)
    boolean existsArrivedAfter(@Param("userId") Long userId,
                               @Param("checkedAt") LocalDateTime checkedAt);

    /**
     * 리뷰 삭제 시 그 리뷰로 이동하는 알림 정리.
     *
     * <p>notifications.review_id에는 DB FK(notifications_review_id_fkey)가 걸려 있어
     * 이 정리를 빠뜨리면 리뷰 삭제 자체가 제약 위반으로 실패한다.
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.reviewId = :reviewId")
    int deleteAllByReviewId(@Param("reviewId") Long reviewId);
}
