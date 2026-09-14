package com.example.Gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(max = 256) String username,
                           @NotBlank @Size(max = 72) String password) {
    @Override
    public String toString() {
        return "LoginRequest[redacted]";
    }
}
