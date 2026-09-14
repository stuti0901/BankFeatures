package com.example.Gateway.service;

import com.example.Gateway.config.AuthProperties;
import com.example.Gateway.dto.LoginResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {
    private final AuthProperties properties;
    private final SecretKey key;
    private final JwtParser parser;

    public JwtService(AuthProperties properties) {
        this.properties = properties;
        try {
            key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.jwtSecret()));
        } catch (IllegalArgumentException | io.jsonwebtoken.security.WeakKeyException exception) {
            throw new IllegalStateException("JWT_SECRET must be Base64 containing at least 32 random bytes");
        }
        parser = Jwts.parser().verifyWith(key)
                .sig().clear().add(Jwts.SIG.HS256).and()
                .requireIssuer(properties.issuer())
                .requireAudience(properties.audience())
                .build();
    }

    public LoginResponse generate(String username) {
        Instant now = Instant.now();
        String token = Jwts.builder().subject(username)
                .issuer(properties.issuer()).audience().add(properties.audience()).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.ttlSeconds())))
                .signWith(key, Jwts.SIG.HS256).compact();
        return new LoginResponse(token, "Bearer", properties.ttlSeconds());
    }

    public String validate(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            if (claims.getSubject() == null || claims.getSubject().isBlank()
                    || claims.getExpiration() == null) {
                throw new BadCredentialsException("Invalid bearer token");
            }
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BadCredentialsException("Invalid bearer token");
        }
    }
}
