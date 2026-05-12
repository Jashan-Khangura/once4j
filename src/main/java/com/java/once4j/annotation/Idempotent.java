package com.java.once4j.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    /**
     * SpEL expression to determine the unique idempotency key.
     * Example: "#request.orderId" or "#header['Idempotency-Key']"
     */
    String keyExpression();

    /**
     * Time-to-live for the response in the store.
     * Default: 15 minutes.
     */
    long responseTTL() default 900000;

    /**
     * Timeout value for the lock on request execution.
     * Default: 30 seconds.
     */
    long execLockTimeout() default 30000;

    /**
     * If true, hashes the request payload to ensure content integrity.
     */
    boolean validatePayload() default true;
}
