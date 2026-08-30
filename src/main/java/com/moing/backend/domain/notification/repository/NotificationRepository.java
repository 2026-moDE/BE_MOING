package com.moing.backend.domain.notification.repository;

import com.moing.backend.domain.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            SELECT n FROM Notification n
            WHERE n.userId = :userId
              AND (:cursor IS NULL OR n.id < :cursor)
            ORDER BY n.id DESC
            """)
    List<Notification> findByUserId(
            @Param("userId") Long userId,
            @Param("cursor") Long cursor,
            Pageable pageable);

    // 미읽음 알림 존재 여부. 파생 쿼리 이름(...IsReadFalse)은 boolean 필드 isRead를
    // read로 볼지 isRead로 볼지 모호해서 JPQL로 명시한다
    @Query("""
            SELECT COUNT(n) > 0 FROM Notification n
            WHERE n.userId = :userId
              AND n.isRead = false
            """)
    boolean existsUnread(@Param("userId") Long userId);
}
