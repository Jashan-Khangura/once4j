package com.java.once4j.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.once4j.annotation.Idempotent;
import com.java.once4j.store.IdempotentStore;
import com.java.once4j.custom.exceptions.IdempotentRequestException;
import com.java.once4j.custom.exceptions.PayloadMismatchException;
import com.java.once4j.custom.serialize.JSONIdempotentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotentAspectTest {

    @Mock
    private IdempotentStore store;

    private TestService proxy;
    private TestService target;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        JSONIdempotentSerializer serializer = new JSONIdempotentSerializer(mapper);
        IdempotentAspect aspect = new IdempotentAspect(store, serializer, mapper);

        target = new TestService();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        proxy = factory.getProxy();
    }

    // --- cache miss: first request ---

    @Test
    void process_firstRequest_executesMethodAndCachesResult() throws Throwable {
        when(store.tryLock(anyString(), anyLong())).thenReturn(true);

        String result = proxy.greet("Alice");

        assertThat(result).isEqualTo("Hello Alice");
        verify(store).save(anyString(), anyString(), anyLong());
    }

    // --- cache hit: second request same payload ---

    @Test
    void process_cacheHit_returnsCachedResult_methodNotCalledAgain() throws Throwable {
        String cachedJson = "{\"payloadHash\":null,\"responseString\":\"\\\"cached-response\\\"\"}";
        when(store.tryLock(anyString(), anyLong())).thenReturn(false);
        when(store.waitForResult(anyString(), anyLong())).thenReturn(cachedJson);

        String result = proxy.greet("Alice");

        assertThat(result).isEqualTo("cached-response");
        verify(store, never()).save(anyString(), anyString(), anyLong());
    }

    // --- payload validation ---

    @Test
    void process_payloadMismatch_throwsPayloadMismatchException() throws Throwable {
        // Store a record with a hash that won't match the incoming request
        String differentHash = "0000000000000000000000000000000000000000000000000000000000000000";
        String cachedJson = "{\"payloadHash\":\"" + differentHash + "\",\"responseString\":\"\\\"old\\\"\"}";

        when(store.tryLock(anyString(), anyLong())).thenReturn(false);
        when(store.waitForResult(anyString(), anyLong())).thenReturn(cachedJson);

        assertThatThrownBy(() -> proxy.greetValidated("Alice"))
                .isInstanceOf(PayloadMismatchException.class);
    }

    @Test
    void process_validatePayloadFalse_ignoresMismatch_returnsCachedResult() throws Throwable {
        String cachedJson = "{\"payloadHash\":\"wrong-hash\",\"responseString\":\"\\\"cached\\\"\"}";
        when(store.tryLock(anyString(), anyLong())).thenReturn(false);
        when(store.waitForResult(anyString(), anyLong())).thenReturn(cachedJson);

        String result = proxy.greetNoValidation("Alice");

        assertThat(result).isEqualTo("cached");
    }

    // --- method throws ---

    @Test
    void process_methodThrows_deletesKeyAndRethrows() {
        when(store.tryLock(anyString(), anyLong())).thenReturn(true);

        assertThatThrownBy(() -> proxy.alwaysFails())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        verify(store).delete(anyString());
    }

    // --- waitForResult returns null ---

    @Test
    void process_waitForResultReturnsNull_throwsIdempotentRequestException() {
        when(store.tryLock(anyString(), anyLong())).thenReturn(false);
        when(store.waitForResult(anyString(), anyLong())).thenReturn(null);

        assertThatThrownBy(() -> proxy.greet("Alice"))
                .isInstanceOf(IdempotentRequestException.class);
    }

    // --- void method ---

    @Test
    void process_voidMethod_savesVoidResultAndReturnsNull() throws Throwable {
        when(store.tryLock(anyString(), anyLong())).thenReturn(true);

        proxy.doNothing("order-1");

        verify(store).save(anyString(), anyString(), anyLong());
    }

    // --- SpEL key expression ---

    @Test
    void process_spelKeyExpression_usesCorrectKey() throws Throwable {
        when(store.tryLock(anyString(), anyLong())).thenReturn(true);

        proxy.greet("Alice");

        // key should be "Alice" (from #name SpEL expression)
        verify(store).tryLock(eq("Alice"), anyLong());
    }

    // --- test service used as AOP target ---

    static class TestService {

        @Idempotent(keyExpression = "#name", validatePayload = false)
        public String greet(String name) {
            return "Hello " + name;
        }

        @Idempotent(keyExpression = "#name", validatePayload = true)
        public String greetValidated(String name) {
            return "Hello " + name;
        }

        @Idempotent(keyExpression = "#name", validatePayload = false)
        public String greetNoValidation(String name) {
            return "Hello " + name;
        }

        @Idempotent(keyExpression = "'fixed-key'")
        public void doNothing(String orderId) {
        }

        @Idempotent(keyExpression = "'error-key'")
        public String alwaysFails() {
            throw new RuntimeException("boom");
        }
    }
}
