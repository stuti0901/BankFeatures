package com.example.Gateway.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        @NotBlank String username,
        @NotBlank @Pattern(regexp = "^\\$2[aby]\\$(0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}$")
        String passwordHash,
        @NotBlank String jwtSecret,
        @NotBlank String issuer,
        @NotBlank String audience,
        @Positive long ttlSeconds) {
    @Override
    public String toString() {
        return "AuthProperties[redacted]";
    }
}
