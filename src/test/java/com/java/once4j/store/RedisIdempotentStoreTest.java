package com.java.once4j.store;

import com.java.once4j.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisIdempotentStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisIdempotentStore store;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        store = new RedisIdempotentStore(redisTemplate);
    }

    // --- tryLock ---

    @Test
    void tryLock_setNxSucceeds_returnsTrue() {
        when(valueOps.setIfAbsent(eq("key"), eq(Constants.LOCKED), any(Duration.class)))
                .thenReturn(true);
        assertThat(store.tryLock("key", 5000)).isTrue();
    }

    @Test
    void tryLock_setNxFails_returnsFalse() {
        when(valueOps.setIfAbsent(eq("key"), eq(Constants.LOCKED), any(Duration.class)))
                .thenReturn(false);
        assertThat(store.tryLock("key", 5000)).isFalse();
    }

    @Test
    void tryLock_redisReturnsNull_returnsFalse() {
        when(valueOps.setIfAbsent(eq("key"), eq(Constants.LOCKED), any(Duration.class)))
                .thenReturn(null);
        assertThat(store.tryLock("key", 5000)).isFalse();
    }

    // --- get ---

    @Test
    void get_lockedValue_returnsNull() {
        when(valueOps.get("key")).thenReturn(Constants.LOCKED);
        assertThat(store.get("key")).isNull();
    }

    @Test
    void get_actualValue_returnsValue() {
        when(valueOps.get("key")).thenReturn("{\"data\":1}");
        assertThat(store.get("key")).isEqualTo("{\"data\":1}");
    }

    @Test
    void get_missingKey_returnsNull() {
        when(valueOps.get("key")).thenReturn(null);
        assertThat(store.get("key")).isNull();
    }

    // --- save ---

    @Test
    void save_callsRedisSetWithTtl() {
        store.save("key", "result", 5000);
        verify(valueOps).set("key", "result", Duration.ofMillis(5000));
    }

    // --- delete ---

    @Test
    void delete_callsRedisDelete() {
        when(redisTemplate.delete("key")).thenReturn(true);
        assertThat(store.delete("key")).isTrue();
        verify(redisTemplate).delete("key");
    }

    // --- waitForResult ---

    @Test
    void waitForResult_keyMissingImmediately_returnsNull() {
        when(valueOps.get("key")).thenReturn(null);
        assertThat(store.waitForResult("key", 3000)).isNull();
    }

    @Test
    void waitForResult_nonLockedValueImmediately_returnsValue() {
        when(valueOps.get("key")).thenReturn("{\"data\":1}");
        assertThat(store.waitForResult("key", 3000)).isEqualTo("{\"data\":1}");
    }

    @Test
    void waitForResult_lockedThenValue_returnsValue() {
        when(valueOps.get("key"))
                .thenReturn(Constants.LOCKED)
                .thenReturn(Constants.LOCKED)
                .thenReturn("{\"data\":1}");

        assertThat(store.waitForResult("key", 3000)).isEqualTo("{\"data\":1}");
    }

    @Test
    void waitForResult_lockedThenNull_returnsNull() {
        when(valueOps.get("key"))
                .thenReturn(Constants.LOCKED)
                .thenReturn(null); // A failed

        assertThat(store.waitForResult("key", 3000)).isNull();
    }
}
