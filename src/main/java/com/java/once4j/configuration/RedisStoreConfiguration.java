package com.java.once4j.configuration;

import com.java.once4j.store.IdempotentStore;
import com.java.once4j.store.RedisIdempotentStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@ConditionalOnClass(RedisTemplate.class)
public class RedisStoreConfiguration {
    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(IdempotentStore.class)
    public IdempotentStore redisIdempotencyStore(StringRedisTemplate redisTemplate) {
        return new RedisIdempotentStore(redisTemplate);
    }
}
