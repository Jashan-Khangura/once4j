package com.java.once4j.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.once4j.annotation.Idempotent;
import com.java.once4j.dto.IdempotentRecord;
import com.java.once4j.store.IdempotentStore;
import custom.exceptions.FailedMarshallingException;
import custom.exceptions.IdempotentRequestException;
import custom.exceptions.PayloadMismatchException;
import custom.serialize.CustomIdempotentSerializer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Aspect
public class IdempotentAspect {
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();
    private final IdempotentStore store;
    private final CustomIdempotentSerializer serializer;
    private final ObjectMapper mapper;

    public IdempotentAspect(IdempotentStore store, CustomIdempotentSerializer serializer, ObjectMapper mapper) {
        this.store = store;
        this.serializer = serializer;
        this.mapper = mapper;
    }

    @Around("@annotation(com.java.once4j.annotation.Idempotent)")
    public Object process(ProceedingJoinPoint point) throws Throwable {
        //Extract caller method's signature
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Class<?> returnType = signature.getReturnType();

        //Get values defined by user
        Idempotent annotation = method.getAnnotation(Idempotent.class);
        String keyExpression = annotation.keyExpression();
        long responseTTL = annotation.responseTTL();
        long executionTTL = annotation.execLockTimeout();
        String key = parseKey(keyExpression, method, point.getArgs());
        boolean validatePayload = annotation.validatePayload();

        if(!store.tryLock(key, executionTTL)) {
            String cachedResponse = store.waitForResult(key, executionTTL);
            if(cachedResponse == null)
                throw new IdempotentRequestException("In-progress request failed or timed out for key : " + key);
            return prepareResponse(cachedResponse, validatePayload, point.getArgs(), returnType, key);
        }

        Object result;
        try {
            result = point.proceed();
        } catch (Throwable t) {
            store.delete(key);
            throw t;
        }

        String hash = validatePayload ? hashArgs(point.getArgs()) : null;
        store.save(key, mapper.writeValueAsString(new IdempotentRecord(hash,
                serializer.serialize(result))), responseTTL);

        return result;
    }

    private Object prepareResponse(String cachedResponse, boolean validatePayload, Object[] args,
                                   Class<?> returnType, String key) throws JsonProcessingException {
        IdempotentRecord record = mapper.readValue(cachedResponse, IdempotentRecord.class);
        if(validatePayload) {
            String requestHash = hashArgs(args);
            if(!requestHash.equals(record.payloadHash())) {
                throw new PayloadMismatchException("Idempotency key " + key + " was already used with a different payload");
            }
        }
        return serializer.deserialize(record.responseString(), returnType);
    }

    private String hashArgs(Object[] args) {
        try{
            String json = mapper.writeValueAsString(args);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        } catch (Exception e) {
            throw new FailedMarshallingException("Failed to hash request payload", e);
        }
    }

    private String parseKey(String expression, Method method, Object[] args) {
        String[] params = discoverer.getParameterNames(method);
        StandardEvaluationContext context = new StandardEvaluationContext();

        for (int i = 0; i < params.length; i++) {
            context.setVariable(params[i], args[i]);
        }

        return parser.parseExpression(expression).getValue(context, String.class);
    }

}
