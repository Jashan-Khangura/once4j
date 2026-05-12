package com.java.once4j.store;

public interface IdempotentStore {
    /**
     * Tries to acquire the lock (SET NX).
     * @return true if acquired, false if already exists.
     */
    boolean tryLock(String key, long ttlMillis);

    /**
     * Gets the stored idempotency record (the hash and the cached response).
     */
    String get(String key);

    /**
     * Saves the result and the hash after the business logic executes.
     */
    void save(String key, String record, long ttlMillis);

    /**
     * Deletes the key (e.g., if the business logic failed and you want to allow a retry).
     */
    Boolean delete(String key);

    /**
     * Blocks until the in-progress request for this key completes or the timeout elapses.
     * Returns the cached JSON result if the request succeeded, or null if it failed/timed out.
     */
    String waitForResult(String key, long timeoutMillis);
}
