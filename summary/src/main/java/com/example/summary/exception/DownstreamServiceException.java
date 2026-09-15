package com.example.summary.exception;

import org.springframework.http.HttpStatus;

public class DownstreamServiceException extends RuntimeException {
    private final HttpStatus status;

    public DownstreamServiceException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}