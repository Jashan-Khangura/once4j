package com.java.once4j.custom.serialize;

public interface CustomIdempotentSerializer {
    String serialize(Object result);
    <T> T deserialize(String result, Class<T> returnType);
}
