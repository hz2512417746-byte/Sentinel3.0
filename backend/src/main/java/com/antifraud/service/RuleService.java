package com.antifraud.service;

import com.antifraud.entity.RuleConfig;
import com.antifraud.repository.RuleConfigRepository;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class RuleService {
    private final RuleConfigRepository ruleRepo;
    // 缓存启用的规则（规则名 → RuleConfig）
    private volatile List<Map<String, Object>> activeRules = new ArrayList<>();
    private volatile List<RuleConfig> enabledRules = new ArrayList<>();

    public RuleService(RuleConfigRepository ruleRepo) {
        this.ruleRepo = ruleRepo;
        reload();
    }

    /** 获取启用的规则对象（消费者使用） */
    public List<RuleConfig> getEnabled() { return enabledRules; }

    /** 重新加载启用规则 */
    public synchronized void reload() {
        List<RuleConfig> rules = ruleRepo.findByEnabledTrue();
        this.enabledRules = new ArrayList<>(rules);
        List<Map<String, Object>> list = new ArrayList<>();
        for (RuleConfig r : rules) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.getName());
            m.put("feature", r.getFeature());
            m.put("threshold", r.getThreshold());
            m.put("weight", r.getWeight());
            m.put("description", r.getDescription());
            m.put("fraudType", r.getFraudType());
            m.put("timeWindow", r.getTimeWindow());
            m.put("riskLevel", r.getRiskLevel());
            m.put("conditionJson", r.getConditionJson());
            list.add(m);
        }
        this.activeRules = list;
    }

    public List<Map<String, Object>> getActiveRules() { return activeRules; }

    public List<RuleConfig> getAllRules() { return ruleRepo.findAll(); }

    public RuleConfig addRule(RuleConfig rule) {
        RuleConfig saved = ruleRepo.save(rule);
        reload();
        return saved;
    }

    public RuleConfig updateRule(Long id, RuleConfig update) {
        RuleConfig r = ruleRepo.findById(id).orElseThrow();
        r.setName(update.getName());
        r.setFeature(update.getFeature());
        r.setThreshold(update.getThreshold());
        r.setWeight(update.getWeight());
        r.setDescription(update.getDescription());
        r.setEnabled(update.getEnabled());
        r.setFraudType(update.getFraudType());
        r.setTimeWindow(update.getTimeWindow());
        r.setRiskLevel(update.getRiskLevel());
        r.setConditionJson(update.getConditionJson());
        RuleConfig saved = ruleRepo.save(r);
        reload();
        return saved;
    }

    public void deleteRule(Long id) {
        ruleRepo.deleteById(id);
        reload();
    }

    public void toggleRule(Long id) {
        RuleConfig r = ruleRepo.findById(id).orElseThrow();
        r.setEnabled(!r.getEnabled());
        ruleRepo.save(r);
        reload();
    }
}
