package com.example.Gateway;

import com.example.Gateway.config.AuthProperties;
import com.example.Gateway.service.JwtService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthConfigurationTests {
    private ApplicationContextRunner configuredContext() {
        return new ApplicationContextRunner().withUserConfiguration(AuthTestConfiguration.class)
                .withPropertyValues(
                        "auth.username=" + UUID.randomUUID(),
                        "auth.password-hash=" + new BCryptPasswordEncoder().encode(UUID.randomUUID().toString()),
                        "auth.jwt-secret=" + Base64.getEncoder().encodeToString(Jwts.SIG.HS256.key().build().getEncoded()),
                        "auth.issuer=" + UUID.randomUUID(),
                        "auth.audience=" + UUID.randomUUID(),
                        "auth.ttl-seconds=300");
    }

    @Test
    void validExternalConfigurationStarts() {
        configuredContext().run(context -> assertThat(context).hasNotFailed().hasSingleBean(JwtService.class));
    }

    @Test
    void missingSecurityValuesFailStartup() {
        for (String name : new String[]{"username", "password-hash", "jwt-secret", "issuer", "audience"}) {
            configuredContext().withPropertyValues("auth." + name + "=")
                    .run(context -> assertThat(context).hasFailed());
        }
        configuredContext().withPropertyValues("auth.ttl-seconds=0")
                .run(context -> assertThat(context).hasFailed());
        new ApplicationContextRunner().withUserConfiguration(AuthTestConfiguration.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void invalidPasswordHashAndWeakOrMalformedKeysFailStartup() {
        configuredContext().withPropertyValues("auth.password-hash=" + UUID.randomUUID())
                .run(context -> assertThat(context).hasFailed());
        configuredContext().withPropertyValues("auth.jwt-secret=%%%")
                .run(context -> assertThat(context).hasFailed());
        configuredContext().withPropertyValues("auth.jwt-secret=" +
                        Base64.getEncoder().encodeToString(new byte[16]))
                .run(context -> assertThat(context).hasFailed());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthProperties.class)
    @Import(JwtService.class)
    static class AuthTestConfiguration {
    }
}
