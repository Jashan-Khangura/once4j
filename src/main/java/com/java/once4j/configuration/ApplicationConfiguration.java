package com.java.once4j.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.java.once4j.aspect.IdempotentAspect;
import com.java.once4j.store.IdempotentStore;
import com.java.once4j.store.LocalIdempotentStore;
import com.java.once4j.custom.serialize.CustomIdempotentSerializer;
import com.java.once4j.custom.serialize.JSONIdempotentSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Fallback;

@AutoConfiguration
public class ApplicationConfiguration {
    @Bean
    @Fallback
    public IdempotentStore localIdempotentStore() {
        return new LocalIdempotentStore();
    }

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper localIdempotentMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    @Bean
    @ConditionalOnMissingBean(CustomIdempotentSerializer.class)
    public CustomIdempotentSerializer localIdempotentSerializer(ObjectMapper localIdempotentMapper) {
        return new JSONIdempotentSerializer(localIdempotentMapper);
    }

    @Bean
    public IdempotentAspect idempotentAspect(IdempotentStore store,
                                             CustomIdempotentSerializer serializer, ObjectMapper mapper) {
        return new IdempotentAspect(store, serializer, mapper);
    }
}
