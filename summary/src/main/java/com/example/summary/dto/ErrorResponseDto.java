package com.example.summary.dto;

public record ErrorResponseDto(String apiPath, org.springframework.http.HttpStatus errorCode,
        String errorMessage, java.time.LocalDateTime errorTime) {
}