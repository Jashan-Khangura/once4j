package com.java.once4j.store;

import com.java.once4j.Constants;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class LocalIdempotentStore implements IdempotentStore{
    private final ConcurrentHashMap<String, LocalStoreEntry> store = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public boolean tryLock(String key, long ttlMillis) {
        lock.lock();
        try {
            if(!store.containsKey(key) || store.get(key).ttl() < System.currentTimeMillis()) {
                store.put(key, new LocalStoreEntry(Constants.LOCKED, System.currentTimeMillis()+ttlMillis));
                return true;
            }
        } finally {
            lock.unlock();
        }
        return false;
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
    }

    @Override
    public Boolean delete(String key) {
        return store.remove(key) != null;
    }
}
