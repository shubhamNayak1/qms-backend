package com.qms.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean     success;
    private final String      message;
    private final T           data;
    private final Object      errors;

    @Builder.Default
    private final LocalDateTime timestamp = LocalDateTime.now();

    public static <T> ResponseEntity<ApiResponse<T>> ok(T data) { return ok("Success", data); }

    public static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data).build());
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(String message, T data) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<T>builder().success(true).message(message).data(data).build());
    }

    public static <T> ResponseEntity<ApiResponse<T>> noContent(String message) {
        return ResponseEntity.ok(ApiResponse.<T>builder().success(true).message(message).build());
    }

    public static <T> ResponseEntity<ApiResponse<T>> error(HttpStatus status, String message) {
        return error(status, message, null);
    }

    public static <T> ResponseEntity<ApiResponse<T>> error(HttpStatus status, String message, Object errors) {
        return ResponseEntity.status(status)
                .body(ApiResponse.<T>builder().success(false).message(message).errors(errors).build());
    }
}
