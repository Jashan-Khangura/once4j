package com.java.once4j.configuration;

import com.java.once4j.store.IdempotentStore;
import com.java.once4j.store.RedisIdempotentStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnClass(RedisTemplate.class)
public class RedisStoreConfiguration {
    @Bean
    @ConditionalOnMissingBean(IdempotentStore.class)
    public IdempotentStore redisIdempotencyStore(StringRedisTemplate redisTemplate) {
        return new RedisIdempotentStore(redisTemplate);
    }
}
