package com.HyperLocalSystem.app.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        int status,
        String code,
        String message,
        Map<String, String> fieldErrors,
        String path,
        Instant timestamp
) {
    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(status, code, message, null, path, Instant.now());
    }
}
