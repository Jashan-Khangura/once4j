package com.java.once4j.aspect;

import com.java.once4j.annotation.Idempotent;
import com.java.once4j.store.IdempotentStore;
import custom.exceptions.DuplicateRequestException;
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
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class IdempotentAspect {
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();
    private final IdempotentStore store;
    private final CustomIdempotentSerializer serializer;

    public IdempotentAspect(IdempotentStore store, CustomIdempotentSerializer serializer) {
        this.store = store;
        this.serializer = serializer;
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

        if (!store.tryLock(key, executionTTL)) {
            throw new DuplicateRequestException("Request in progress");
        }

        try {
            //Check Cache if response present
            String cachedResponse = store.get(key);
            if(cachedResponse != null)
                return serializer.deserialize(cachedResponse, returnType);
            Object result = point.proceed();

            //Cache result and return
            store.save(key, serializer.serialize(result), responseTTL);
            return result;
        } catch (Throwable t) {
            store.delete(key);
            throw t;
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
