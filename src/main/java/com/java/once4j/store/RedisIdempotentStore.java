package com.java.once4j.store;

import com.java.once4j.Constants;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

public class RedisIdempotentStore implements IdempotentStore {
    private final StringRedisTemplate redisTemplate;

    public RedisIdempotentStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(String key, long ttlMillis) {
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, Constants.LOCKED,
                Duration.ofMillis(ttlMillis));

        return Boolean.TRUE.equals(success);
    }

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void save(String key, String jsonrecord, long ttlMillis) {
        redisTemplate.opsForValue().set(key, jsonrecord, Duration.ofMillis(ttlMillis));
    }

    @Override
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }
}
