package com.java.once4j.store;

import com.java.once4j.Constants;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class LocalIdempotentStore implements IdempotentStore {
    private final ConcurrentHashMap<String, LocalStoreEntry> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String key, long ttlMillis) {
        AtomicBoolean acquired = new AtomicBoolean(false);
        store.compute(key, (k, existing) -> {
            if (existing == null || existing.ttl() < System.currentTimeMillis()) {
                acquired.set(true);
                pending.put(k, new CompletableFuture<>());
                return new LocalStoreEntry(Constants.LOCKED,
                        System.currentTimeMillis() + ttlMillis);
            }
            return existing;
        });
        return acquired.get();
    }

    @Override
    public String get(String key) {
        LocalStoreEntry entry = store.get(key);
        if(entry == null) return null;
        if(entry.ttl() < System.currentTimeMillis()) {
            delete(key);
            return null;
        }
        if (Constants.LOCKED.equals(entry.value())) return null;
        return entry.value();
    }

    @Override
    public void save(String key, String record, long ttlMillis) {
        store.put(key, new LocalStoreEntry(record, System.currentTimeMillis()+ttlMillis));
        CompletableFuture<String> future = pending.remove(key);
        if(future != null) future.complete(record);
    }

    @Override
    public Boolean delete(String key) {
        Boolean removed = store.remove(key) != null;
        CompletableFuture<String> future = pending.remove(key);
        if(future != null) future.complete(null);
        return removed;
    }

    @Override
    public String waitForResult(String key, long timeoutMillis) {
        CompletableFuture<String> future = pending.get(key);
        if (future == null) return get(key);
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
