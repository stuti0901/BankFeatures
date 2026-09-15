package com.example.summary.exception;

import com.example.summary.dto.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> notFound(
            ResourceNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(DownstreamServiceException.class)
    public ResponseEntity<ErrorResponseDto> downstream(
            DownstreamServiceException exception, HttpServletRequest request) {
        return error(exception.getStatus(), exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponseDto> invalidInput(
            ConstraintViolationException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Mobile number must be 10 digits", request.getRequestURI());
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(exception, "Mobile number must be 10 digits",
                headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpStatus errorStatus = HttpStatus.valueOf(status.value());
        String message = body instanceof String text ? text
                : errorStatus == HttpStatus.BAD_REQUEST ? "Invalid request: mobileNumber is required"
                : errorStatus.getReasonPhrase();
        return super.handleExceptionInternal(exception,
                new ErrorResponseDto(request.getDescription(false).replaceFirst("^uri=", ""),
                        errorStatus, message, LocalDateTime.now()), headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> unexpected(
            Exception exception, HttpServletRequest request) {
        logger.error("Unexpected customer summary failure", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to retrieve customer summary",
                request.getRequestURI());
    }

    private ResponseEntity<ErrorResponseDto> error(HttpStatus status, String message, String path) {
        return ResponseEntity.status(status)
                .body(new ErrorResponseDto(path, status, message, LocalDateTime.now()));
    }
}