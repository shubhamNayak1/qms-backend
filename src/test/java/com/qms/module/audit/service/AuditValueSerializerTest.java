package com.qms.module.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AuditValueSerializer")
class AuditValueSerializerTest {

    private AuditValueSerializer serializer;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        serializer = new AuditValueSerializer(mapper);
        ReflectionTestUtils.setField(serializer, "sensitiveFields",
                List.of("password", "passwordHash", "refreshTokenHash", "creditCard", "cvv"));
    }

    @Data
    static class UserDto {
        private Long   id       = 1L;
        private String username = "john.doe";
        private String passwordHash = "$2a$12$realHash";
        private String refreshTokenHash = "$2a$12$tokenHash";
    }

    @Data
    static class PaymentDto {
        private String creditCard = "4111111111111111";
        private String cvv        = "123";
        private double amount     = 99.99;
    }

    @Test
    @DisplayName("serializes a plain object to JSON")
    void serializesPlainObject() {
        @Data class SimpleDto { String name = "test"; int value = 42; }
        String json = serializer.serialize(new SimpleDto());

        assertThat(json).isNotNull().contains("\"name\"").contains("\"test\"").contains("42");
    }

    @Test
    @DisplayName("masks password fields")
    void masksPasswordFields() {
        String json = serializer.serialize(new UserDto());

        assertThat(json).doesNotContain("$2a$12$realHash");
        assertThat(json).contains("***MASKED***");
        // Non-sensitive fields are preserved
        assertThat(json).contains("john.doe");
    }

    @Test
    @DisplayName("masks multiple sensitive fields in same object")
    void masksMultipleSensitiveFields() {
        String json = serializer.serialize(new UserDto());

        assertThat(json).doesNotContain("$2a$12$realHash");
        assertThat(json).doesNotContain("$2a$12$tokenHash");
        // Should have two masked values
        int count = json.split("\\*\\*\\*MASKED\\*\\*\\*").length - 1;
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("masks credit card and CVV fields")
    void masksPaymentFields() {
        String json = serializer.serialize(new PaymentDto());

        assertThat(json).doesNotContain("4111111111111111");
        assertThat(json).doesNotContain("\"123\"");
        assertThat(json).contains("99.99");  // amount is not sensitive
    }

    @Test
    @DisplayName("returns null for null input")
    void returnsNullForNullInput() {
        assertThat(serializer.serialize(null)).isNull();
        assertThat(serializer.serializeIfPresent(null)).isNull();
    }

    @Test
    @DisplayName("serializes primitive strings directly")
    void serializesPrimitiveString() {
        String json = serializer.serialize("hello world");
        assertThat(json).isEqualTo("\"hello world\"");
    }

    @Test
    @DisplayName("returns error JSON when serialization fails")
    void returnsErrorJsonOnFailure() {
        // An object that cannot be serialized
        Object unserializable = new Object() {
            public Object getCycle() { return this; } // circular reference
        };

        String result = serializer.serialize(unserializable);
        assertThat(result).contains("_error").contains("serialization failed");
    }

    @Test
    @DisplayName("truncates very large values")
    void truncatesLargeValues() {
        String huge = "x".repeat(70_000);
        String result = serializer.serialize(huge);

        assertThat(result.length()).isLessThanOrEqualTo(65_100);  // TEXT column safe limit + small overhead
        assertThat(result).contains("TRUNCATED");
    }
}
