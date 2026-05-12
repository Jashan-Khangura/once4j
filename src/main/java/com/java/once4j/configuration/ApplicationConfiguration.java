package com.java.once4j.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.once4j.store.IdempotentStore;
import com.java.once4j.store.LocalIdempotentStore;
import custom.serialize.CustomIdempotentSerializer;
import custom.serialize.JSONIdempotentSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ApplicationConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "settings", name = "mode", havingValue = "local")
    public IdempotentStore localIdempotentStore() {
        return new LocalIdempotentStore();
    }

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper localIdempotentMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean(CustomIdempotentSerializer.class)
    public CustomIdempotentSerializer localIdempotentSerializer(ObjectMapper localIdempotentMapper) {
        return new JSONIdempotentSerializer(localIdempotentMapper);
    }
}
