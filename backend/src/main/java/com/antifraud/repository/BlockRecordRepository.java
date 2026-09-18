package com.antifraud.repository;

import com.antifraud.entity.BlockRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

public interface BlockRecordRepository extends JpaRepository<BlockRecord, Long> {
    List<BlockRecord> findByActiveTrue();

    @Query("SELECT b FROM BlockRecord b WHERE b.createdAt >= :since ORDER BY b.createdAt DESC")
    List<BlockRecord> findSince(LocalDateTime since);

    @Modifying @Transactional
    @Query("UPDATE BlockRecord b SET b.active=false WHERE b.userId=:userId")
    void deactivateByUser(@Param("userId") String userId);

    @Modifying @Transactional
    @Query("UPDATE BlockRecord b SET b.active=false WHERE b.userId=:userId AND b.actionType=:type")
    void deactivateByUserAndType(@Param("userId") String userId, @Param("type") String type);
}
