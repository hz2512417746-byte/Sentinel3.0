package com.antifraud.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {
    private static final String BAN_KEY = "banned_users";
    private static final String INTERCEPT_KEY = "intercepted_users";
    private static final String ZK_PREFIX = "zk:";

    private final RedisTemplate<String, String> redis;

    public RedisService(RedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    public boolean isBanned(String userId) {
        Boolean b = redis.opsForSet().isMember(BAN_KEY, userId);
        return b != null && b;
    }

    public void banUser(String userId) {
        redis.opsForSet().add(BAN_KEY, userId);
        redis.opsForSet().remove(INTERCEPT_KEY, userId);
    }

    public void unbanUser(String userId) {
        redis.opsForSet().remove(BAN_KEY, userId);
    }

    public void interceptUser(String userId) {
        if (!isBanned(userId)) {
            redis.opsForSet().add(INTERCEPT_KEY, userId);
        }
    }

    public void removeIntercept(String userId) {
        redis.opsForSet().remove(INTERCEPT_KEY, userId);
    }

    public Set<String> getBannedUsers() {
        return redis.opsForSet().members(BAN_KEY);
    }

    public Set<String> getInterceptedUsers() {
        return redis.opsForSet().members(INTERCEPT_KEY);
    }

    public void storeFeatures(String userId, Map<String, Double> features) {
        String key = ZK_PREFIX + userId;
        for (Map.Entry<String, Double> e : features.entrySet()) {
            redis.opsForHash().put(key, e.getKey(), String.valueOf(e.getValue()));
        }
        redis.expire(key, 60, TimeUnit.MINUTES);
    }

    public Map<String, Double> getFeatures(String userId) {
        String key = ZK_PREFIX + userId;
        Map<Object, Object> raw = redis.opsForHash().entries(key);
        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<Object, Object> e : raw.entrySet()) {
            try { result.put((String) e.getKey(), Double.parseDouble((String) e.getValue())); }
            catch (Exception ignored) {}
        }
        return result.isEmpty() ? null : result;
    }
}
