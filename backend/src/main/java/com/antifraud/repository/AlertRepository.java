package com.antifraud.repository;

import com.antifraud.entity.Alert;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, String> {
    List<Alert> findByStatusOrderByTimestampDesc(String status);
    long countByStatus(String status);

    /** 自动封禁专用：按月统计用户「警告及以上」(alertLevel≥2) 告警数，忽略关注级，替代 findAll() 全表扫描 */
    @Query("SELECT COUNT(a) FROM Alert a WHERE a.userId=:userId AND a.timestamp>:since AND a.alertLevel>=2")
    long countByUserSince(@Param("userId") String userId, @Param("since") java.time.LocalDateTime since);

    /** 时间范围计数，替代 findAll() 全表扫描 */
    long countByTimestampAfter(java.time.LocalDateTime since);

    /** 分页查询时间范围内的告警 */
    List<Alert> findByTimestampAfterOrderByTimestampDesc(java.time.LocalDateTime since, org.springframework.data.domain.Pageable pageable);

    /** 按用户+时间范围分页查询 */
    @Query("SELECT a FROM Alert a WHERE a.timestamp>:since AND (:userId IS NULL OR a.userId LIKE %:userId%) ORDER BY a.timestamp DESC")
    List<Alert> findByUserAndSince(@Param("userId") String userId, @Param("since") java.time.LocalDateTime since, org.springframework.data.domain.Pageable pageable);

    /** 按用户+时间范围计数 */
    @Query("SELECT COUNT(a) FROM Alert a WHERE a.timestamp>:since AND (:userId IS NULL OR a.userId LIKE %:userId%)")
    long countByUserAndSince(@Param("userId") String userId, @Param("since") java.time.LocalDateTime since);

    @Modifying @Transactional
    @Query("UPDATE Alert a SET a.status='resolved' WHERE a.userId=:userId AND a.status='pending'")
    void resolveByUser(@Param("userId") String userId);

    @Modifying @Transactional
    @Query("UPDATE Alert a SET a.status='resolved' WHERE a.userId=:userId AND a.status='pending'")
    void resolveByUserId(@Param("userId") String userId);
}
