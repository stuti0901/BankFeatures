package com.example.Gateway.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
}
