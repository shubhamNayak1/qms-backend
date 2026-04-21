package com.qms.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String     errorCode;

    public AppException(HttpStatus httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode  = errorCode;
    }

    public static AppException notFound(String resource, Object id) {
        return new AppException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                resource + " not found with id: " + id);
    }

    public static AppException notFound(String message) {
        return new AppException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static AppException badRequest(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static AppException forbidden(String message) {
        return new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static AppException conflict(String message) {
        return new AppException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static AppException internalError(String message) {
        return new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", message);
    }

    public static AppException invalidToken() {
        return new AppException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                "The provided token is invalid.");
    }

    public static AppException tokenExpired() {
        return new AppException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED",
                "The provided token has expired. Please log in again.");
    }

    public static AppException unauthorized(String message) {
        return new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public static AppException accountLocked() {
        return new AppException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED",
                "Your account has been temporarily locked due to multiple failed login attempts.");
    }

    public static AppException accountDisabled() {
        return new AppException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED",
                "Your account has been disabled. Please contact your administrator.");
    }

}
