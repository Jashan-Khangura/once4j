package custom.serialize;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import custom.exceptions.FailedMarshallingException;

public class JSONIdempotentSerializer implements CustomIdempotentSerializer{
    private final ObjectMapper mapper;

    public JSONIdempotentSerializer(ObjectMapper mapper) {
        this.mapper = mapper.registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);;
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
