package com.antifraud.repository;

import com.antifraud.entity.LogEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface LogEventRepository extends JpaRepository<LogEvent, String> {
    Page<LogEvent> findByUserIdContaining(String userId, Pageable pageable);
    Page<LogEvent> findBySrcIpContaining(String srcIp, Pageable pageable);
    Page<LogEvent> findByEventTypeContaining(String eventType, Pageable pageable);
    Page<LogEvent> findByRiskLevelGreaterThanEqual(Integer level, Pageable pageable);

    @Query("SELECT l FROM LogEvent l WHERE "
         + "(:userId IS NULL OR l.userId LIKE %:userId%) AND "
         + "(:srcIp IS NULL OR l.srcIp LIKE %:srcIp%) AND "
         + "(:eventType IS NULL OR l.eventType LIKE %:eventType%) AND "
         + "(:riskLevel IS NULL OR l.riskLevel >= :riskLevel) "
         + "ORDER BY l.timestamp DESC")
    Page<LogEvent> search(@Param("userId") String userId,
                          @Param("srcIp") String srcIp,
                          @Param("eventType") String eventType,
                          @Param("riskLevel") Integer riskLevel,
                          Pageable pageable);

    long countByTimestampAfter(LocalDateTime since);
    long countByFraudLabel(Integer label);
    long countByRiskLevelGreaterThanEqual(Integer level);
    long countByIsBlockedTrue();
    long countByDecision(String decision);

    List<LogEvent> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);

    @Query("SELECT DISTINCT l.userId FROM LogEvent l WHERE l.hitRules IS NOT NULL AND l.hitRules != ''")
    List<String> findFlaggedUsers();

    @Query("SELECT COUNT(l) FROM LogEvent l WHERE l.hitRules LIKE CONCAT('%「',:ruleName,'」%')")
    long countByRuleName(@Param("ruleName") String ruleName);

    @Query("SELECT l.hitRules FROM LogEvent l WHERE l.hitRules IS NOT NULL AND l.hitRules != ''")
    List<Object[]> findHitRulesForSeeding();

    @Query("SELECT l FROM LogEvent l WHERE l.hitRules LIKE %:ruleName% ORDER BY l.timestamp DESC")
    List<LogEvent> findByHitRulesContaining(@Param("ruleName") String ruleName, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT l FROM LogEvent l WHERE l.hitRules LIKE %:ruleName%")
    List<LogEvent> findByHitRulesContaining(@Param("ruleName") String ruleName);
}
