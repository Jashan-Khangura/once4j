package com.java.once4j.custom.serialize;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.once4j.custom.exceptions.FailedMarshallingException;

public class JSONIdempotentSerializer implements CustomIdempotentSerializer{
    private final ObjectMapper mapper;

    public JSONIdempotentSerializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String serialize(Object result) {
        if (result == null) return "VOID_RESULT";
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new FailedMarshallingException("Failed to serialize response", e);
        }
    }

    @Override
    public <T> T deserialize(String result, Class<T> returnType) {
        if("VOID_RESULT".equals(result)) return null;
        try {
            return mapper.readValue(result, returnType);
        } catch (JsonProcessingException e) {
            throw new FailedMarshallingException("Failed to deserialize response", e);
        }
    }
}
