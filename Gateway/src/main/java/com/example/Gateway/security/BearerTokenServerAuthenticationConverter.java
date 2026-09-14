package com.example.Gateway.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

public class BearerTokenServerAuthenticationConverter implements ServerAuthenticationConverter {
    private static final Pattern BEARER = Pattern.compile(
            "^Bearer ([A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)$",
            Pattern.CASE_INSENSITIVE);

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        var headers = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (headers == null || headers.isEmpty()) {
            return Mono.empty();
        }
        if (headers.size() != 1) {
            return Mono.error(new BadCredentialsException("Invalid authorization header"));
        }
        var matcher = BEARER.matcher(headers.get(0));
        if (!matcher.matches()) {
            return Mono.error(new BadCredentialsException("Invalid authorization header"));
        }
        return Mono.just(UsernamePasswordAuthenticationToken.unauthenticated(null, matcher.group(1)));
    }
}
