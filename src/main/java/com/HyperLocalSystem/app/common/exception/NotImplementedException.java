package com.HyperLocalSystem.app.common.exception;

import org.springframework.http.HttpStatus;

/** Thrown by API stubs whose business logic has not been written yet. Returns HTTP 501. */
public class NotImplementedException extends ApiException {

    public NotImplementedException(String feature) {
        super(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", feature + " is not implemented yet");
    }
}
