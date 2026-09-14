package com.example.Gateway.controller;

import com.example.Gateway.dto.LoginRequest;
import com.example.Gateway.dto.LoginResponse;
import com.example.Gateway.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@RestController
public class AuthController {
    private final UserDetailsRepositoryReactiveAuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final ServerAuthenticationEntryPoint unauthorized;

    public AuthController(UserDetailsRepositoryReactiveAuthenticationManager authenticationManager,
                          JwtService jwtService, ServerAuthenticationEntryPoint unauthorized) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.unauthorized = unauthorized;
    }

    @PostMapping(value = "/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                     ServerWebExchange exchange) {
        return Mono.defer(() -> {
            if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
                return Mono.error(new BadCredentialsException("Invalid credentials"));
            }
            return authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        }).map(authentication -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(jwtService.generate(authentication.getName())))
                .onErrorResume(AuthenticationException.class,
                        exception -> unauthorized.commence(exchange, exception).then(Mono.empty()));
    }
}
