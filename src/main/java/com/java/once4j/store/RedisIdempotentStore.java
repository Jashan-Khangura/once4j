package com.java.once4j.store;

import com.java.once4j.Constants;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

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
        String value = redisTemplate.opsForValue().get(key);
        if (Constants.LOCKED.equals(value)) return null;
        return value;
    }

    @Override
    public void save(String key, String jsonrecord, long ttlMillis) {
        redisTemplate.opsForValue().set(key, jsonrecord, Duration.ofMillis(ttlMillis));
    }

    @Override
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    @Override
    public String waitForResult(String key, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            int jitter = ThreadLocalRandom.current().nextInt(0, 50);
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) return null;
            if (!Constants.LOCKED.equals(value)) return value;
            try {
                Thread.sleep(100+jitter);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        //Time Out
        return null;
    }
}
