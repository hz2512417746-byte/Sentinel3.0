package com.antifraud.repository;

import com.antifraud.entity.RuleConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RuleConfigRepository extends JpaRepository<RuleConfig, Long> {
    List<RuleConfig> findByEnabledTrue();
}
