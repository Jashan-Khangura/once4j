package com.java.once4j.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LocalIdempotentStoreTest {

    private LocalIdempotentStore store;

    @BeforeEach
    void setUp() {
        store = new LocalIdempotentStore();
    }

    // --- tryLock ---

    @Test
    void tryLock_newKey_returnsTrue() {
        assertThat(store.tryLock("key", 5000)).isTrue();
    }

    @Test
    void tryLock_existingNonExpiredKey_returnsFalse() {
        store.tryLock("key", 5000);
        assertThat(store.tryLock("key", 5000)).isFalse();
    }

    @Test
    void tryLock_expiredKey_returnsTrue() throws InterruptedException {
        store.tryLock("key", 100);
        Thread.sleep(150);
        assertThat(store.tryLock("key", 5000)).isTrue();
    }

    @Test
    void tryLock_afterDelete_returnsTrue() {
        store.tryLock("key", 5000);
        store.delete("key");
        assertThat(store.tryLock("key", 5000)).isTrue();
    }

    @Test
    void tryLock_concurrent_onlyOneAcquires() throws InterruptedException {
        int threads = 10;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger acquired = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    if (store.tryLock("key", 5000)) acquired.incrementAndGet();
                } catch (InterruptedException ignored) {}
            });
        }

        start.countDown();
        executor.shutdown();
        executor.awaitTermination(3, TimeUnit.SECONDS);

        assertThat(acquired.get()).isEqualTo(1);
    }

    // --- get ---

    @Test
    void get_missingKey_returnsNull() {
        assertThat(store.get("missing")).isNull();
    }

    @Test
    void get_lockedKey_returnsNull() {
        store.tryLock("key", 5000);
        assertThat(store.get("key")).isNull();
    }

    @Test
    void get_savedKey_returnsValue() {
        store.tryLock("key", 5000);
        store.save("key", "result", 5000);
        assertThat(store.get("key")).isEqualTo("result");
    }

    @Test
    void get_expiredKey_returnsNull() throws InterruptedException {
        store.tryLock("key", 5000);
        store.save("key", "result", 100);
        Thread.sleep(150);
        assertThat(store.get("key")).isNull();
    }

    // --- save ---

    @Test
    void save_completesWaitingFuture() throws Exception {
        store.tryLock("key", 5000);

        Future<String> waiting = Executors.newSingleThreadExecutor()
                .submit(() -> store.waitForResult("key", 3000));

        Thread.sleep(50);
        store.save("key", "result", 5000);

        assertThat(waiting.get(2, TimeUnit.SECONDS)).isEqualTo("result");
    }

    // --- delete ---

    @Test
    void delete_existingKey_returnsTrue() {
        store.tryLock("key", 5000);
        assertThat(store.delete("key")).isTrue();
    }

    @Test
    void delete_missingKey_returnsFalse() {
        assertThat(store.delete("missing")).isFalse();
    }

    @Test
    void delete_completesPendingFutureWithNull() throws Exception {
        store.tryLock("key", 5000);

        Future<String> waiting = Executors.newSingleThreadExecutor()
                .submit(() -> store.waitForResult("key", 3000));

        Thread.sleep(50);
        store.delete("key");

        assertThat(waiting.get(2, TimeUnit.SECONDS)).isNull();
    }

    // --- waitForResult ---

    @Test
    void waitForResult_alreadySaved_returnsValueImmediately() {
        store.tryLock("key", 5000);
        store.save("key", "cached", 5000);
        assertThat(store.waitForResult("key", 1000)).isEqualTo("cached");
    }

    @Test
    void waitForResult_missingKey_returnsNull() {
        assertThat(store.waitForResult("missing", 300)).isNull();
    }

    @Test
    void waitForResult_timeout_returnsNull() {
        store.tryLock("key", 5000);
        // nothing saves or deletes — must timeout
        assertThat(store.waitForResult("key", 300)).isNull();
    }
}
