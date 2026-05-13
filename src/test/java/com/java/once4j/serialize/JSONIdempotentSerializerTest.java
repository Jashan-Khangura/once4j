package com.java.once4j.serialize;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.once4j.custom.exceptions.FailedMarshallingException;
import com.java.once4j.custom.serialize.JSONIdempotentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JSONIdempotentSerializerTest {

    private JSONIdempotentSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new JSONIdempotentSerializer(new ObjectMapper());
    }

    // --- serialize ---

    @Test
    void serialize_null_returnsVoidResult() {
        assertThat(serializer.serialize(null)).isEqualTo("VOID_RESULT");
    }

    @Test
    void serialize_object_returnsJson() {
        String json = serializer.serialize(new SampleDto("hello", 42));
        assertThat(json).contains("\"name\":\"hello\"").contains("\"value\":42");
    }

    @Test
    void serialize_string_returnsQuotedJson() {
        assertThat(serializer.serialize("test")).isEqualTo("\"test\"");
    }

    @Test
    void serialize_nonSerializableObject_throwsFailedMarshallingException() {
        Object nonSerializable = new Object() {
            public Object self = this; // circular reference
        };
        assertThatThrownBy(() -> serializer.serialize(nonSerializable))
                .isInstanceOf(FailedMarshallingException.class);
    }

    // --- deserialize ---

    @Test
    void deserialize_voidResult_returnsNull() {
        assertThat(serializer.deserialize("VOID_RESULT", Void.class)).isNull();
    }

    @Test
    void deserialize_validJson_returnsObject() {
        String json = "{\"name\":\"hello\",\"value\":42}";
        SampleDto result = serializer.deserialize(json, SampleDto.class);
        assertThat(result.name()).isEqualTo("hello");
        assertThat(result.value()).isEqualTo(42);
    }

    @Test
    void deserialize_invalidJson_throwsFailedMarshallingException() {
        assertThatThrownBy(() -> serializer.deserialize("not-json", SampleDto.class))
                .isInstanceOf(FailedMarshallingException.class);
    }

    @Test
    void serializeAndDeserialize_roundTrip_producesEqualObject() {
        SampleDto original = new SampleDto("round-trip", 99);
        String json = serializer.serialize(original);
        SampleDto result = serializer.deserialize(json, SampleDto.class);
        assertThat(result.name()).isEqualTo(original.name());
        assertThat(result.value()).isEqualTo(original.value());
    }

    record SampleDto(String name, int value) {}
}
