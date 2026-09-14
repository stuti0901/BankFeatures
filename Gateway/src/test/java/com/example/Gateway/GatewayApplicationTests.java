package com.example.Gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayApplicationTests {
    private static final HttpServer accounts = backend("accounts");
    private static final HttpServer cards = backend("cards");
    private static final HttpServer loans = backend("loans");
    @Autowired WebTestClient client;

    private static HttpServer backend(String name) {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                backendRequests.incrementAndGet();
                byte[] body = (name + ":" + exchange.getRequestURI()).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            server.start();
            return server;
        } catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    @DynamicPropertySource
    static void backends(DynamicPropertyRegistry properties) {
        properties.add("AUTH_USERNAME", () -> username);
        properties.add("AUTH_PASSWORD_HASH", () -> new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password));
        properties.add("JWT_SECRET", () -> java.util.Base64.getEncoder().encodeToString(signingKey.getEncoded()));
        properties.add("JWT_ISSUER", () -> issuer);
        properties.add("JWT_AUDIENCE", () -> audience);
        properties.add("JWT_TTL_SECONDS", () -> 300);

        HttpServer[] servers = {accounts, cards, loans};
        String[] names = {"ACCOUNTS_SERVICE_URL", "CARDS_SERVICE_URL", "LOANS_SERVICE_URL"};
        for (int i = 0; i < servers.length; i++) {
            final HttpServer server = servers[i];
            properties.add(names[i],
                    () -> "http://localhost:" + server.getAddress().getPort());
        }
    }

    @Test
    void rootAndLoginRenderExistingLoginPage() {
        for (String path : new String[]{"/", "/login"}) {
            client.get().uri(path).exchange().expectStatus().isOk()
                    .expectBody(String.class).value(body ->
                        org.assertj.core.api.Assertions.assertThat(body)
                                .contains("Bank Portal", "<h2>Login</h2>", "name=\"username\"", "name=\"password\""));
        }
    }

    @AfterAll
    static void stopBackends() {
        accounts.stop(0);
        cards.stop(0);
        loans.stop(0);
    }

    @Test
    void routesKeepQueriesAndRewriteOnlyCardsAndLoansPrefixes() {
        String token = login();
        client.get().uri("/accounts/api/fetch?mobileNumber=9876543210").header("Authorization", "Bearer " + token).exchange()
                .expectStatus().isOk().expectBody(String.class)
                .isEqualTo("accounts:/accounts/api/fetch?mobileNumber=9876543210");
        client.get().uri("/cards/api/fetch?mobileNumber=9876543210").header("Authorization", "Bearer " + token).exchange()
                .expectStatus().isOk().expectBody(String.class)
                .isEqualTo("cards:/api/fetch?mobileNumber=9876543210");
        client.get().uri("/loans/api/fetch?mobileNumber=9876543210").header("Authorization", "Bearer " + token).exchange()
                .expectStatus().isOk().expectBody(String.class)
                .isEqualTo("loans:/api/fetch?mobileNumber=9876543210");
        client.get().uri("/api/fetch").exchange().expectStatus().isNotFound();
    }

    @Test
    void dashboardRendersSelectedService() {
        client.get().uri("/dashboard").exchange().expectStatus().isOk()
                .expectBody(String.class).value(body ->
                    org.assertj.core.api.Assertions.assertThat(body).contains("Bank Services"));
        client.get().uri("/dashboard?service=cards").exchange().expectStatus().isOk()
                .expectBody(String.class).value(body -> {
                    org.assertj.core.api.Assertions.assertThat(body).contains("Card Services", "/dashboard?service=loans");
                });
    }

    private static final String username = java.util.UUID.randomUUID().toString();
    private static final String password = java.util.UUID.randomUUID().toString();
    private static final javax.crypto.SecretKey signingKey = io.jsonwebtoken.Jwts.SIG.HS256.key().build();
    private static final String issuer = java.util.UUID.randomUUID().toString();
    private static final String audience = java.util.UUID.randomUUID().toString();
    private static final java.util.concurrent.atomic.AtomicInteger backendRequests =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final String[] protectedPaths = {
            "/accounts/api/fetch", "/cards/api/fetch", "/loans/api/fetch"
    };

    private String login() {
        return client.post().uri("/auth/login").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(java.util.Map.of("username", username, "password", password))
                .exchange().expectStatus().isOk()
                .expectHeader().doesNotExist("Set-Cookie")
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectBody(com.example.Gateway.dto.LoginResponse.class)
                .returnResult().getResponseBody().accessToken();
    }

    @Test
    void successfulLoginReturnsSignedExpiringJwt() {
        String token = login();
        var claims = io.jsonwebtoken.Jwts.parser().verifyWith(signingKey)
                .requireIssuer(issuer).requireAudience(audience).build()
                .parseSignedClaims(token).getPayload();
        org.assertj.core.api.Assertions.assertThat(claims.getSubject()).isEqualTo(username);
        org.assertj.core.api.Assertions.assertThat(
                claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(300_000);
        org.assertj.core.api.Assertions.assertThat(claims).doesNotContainKeys("password", "passwordHash");
        client.post().uri("/auth/login").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(java.util.Map.of("username", username, "password", password))
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.expiresIn").isEqualTo(300);
    }

    @Test
    void loginAcceptsCorrectCredentialsButRejectsSwappedCredentialsAndWrongPassword() {
        org.assertj.core.api.Assertions.assertThat(username).isNotEqualTo(password);
        client.post().uri("/auth/login").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(java.util.Map.of("username", username, "password", password))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.accessToken").isNotEmpty();

        client.post().uri("/auth/login").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(java.util.Map.of("username", password, "password", username))
                .exchange().expectStatus().isUnauthorized();

        client.post().uri("/auth/login").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(java.util.Map.of("username", username, "password", password + "-wrong"))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void invalidUsernameOrPasswordReturns401() {
        for (var credentials : java.util.List.of(
                java.util.Map.of("username", username, "password", java.util.UUID.randomUUID().toString()),
                java.util.Map.of("username", java.util.UUID.randomUUID().toString(), "password", password))) {
            client.post().uri("/auth/login").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(credentials).exchange().expectStatus().isUnauthorized()
                    .expectHeader().doesNotExist("Set-Cookie")
                    .expectBody().jsonPath("$.error").isEqualTo("Unauthorized");
        }
    }

    @Test
    void missingJwtReturns401ForEveryServiceAndHttpMethod() {
        int before = backendRequests.get();
        for (String path : protectedPaths) {
            for (var method : java.util.List.of(org.springframework.http.HttpMethod.GET,
                    org.springframework.http.HttpMethod.POST, org.springframework.http.HttpMethod.PUT,
                    org.springframework.http.HttpMethod.DELETE)) {
                client.method(method).uri(path).exchange().expectStatus().isUnauthorized()
                        .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                        .expectHeader().doesNotExist("Location")
                        .expectHeader().doesNotExist("Set-Cookie");
            }
        }
        org.assertj.core.api.Assertions.assertThat(backendRequests.get()).isEqualTo(before);
    }

    @Test
    void malformedInvalidTamperedAndExpiredTokensNeverReachBackends() {
        String valid = login();
        String[] parts = valid.split("\\.");
        byte[] signature = java.util.Base64.getUrlDecoder().decode(parts[2]);
        signature[0] ^= 1;
        String tampered = parts[0] + "." + parts[1] + "." +
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        var now = java.time.Instant.now();
        String expired = tokenBuilder().expiration(java.util.Date.from(now.minusSeconds(30)))
                .signWith(signingKey, io.jsonwebtoken.Jwts.SIG.HS256).compact();
        String wrongKey = tokenBuilder().expiration(java.util.Date.from(now.plusSeconds(300)))
                .signWith(io.jsonwebtoken.Jwts.SIG.HS256.key().build()).compact();
        String wrongIssuer = tokenBuilder().issuer(java.util.UUID.randomUUID().toString())
                .expiration(java.util.Date.from(now.plusSeconds(300))).signWith(signingKey).compact();
        String wrongAudience = tokenBuilder().audience().clear().add(java.util.UUID.randomUUID().toString()).and()
                .expiration(java.util.Date.from(now.plusSeconds(300))).signWith(signingKey).compact();
        String noExpiry = tokenBuilder().signWith(signingKey).compact();
        String noSubject = tokenBuilder().subject(null)
                .expiration(java.util.Date.from(now.plusSeconds(300))).signWith(signingKey).compact();
        String unsigned = tokenBuilder().expiration(java.util.Date.from(now.plusSeconds(300))).compact();
        String future = tokenBuilder().notBefore(java.util.Date.from(now.plusSeconds(300)))
                .expiration(java.util.Date.from(now.plusSeconds(600))).signWith(signingKey).compact();
        String wrongAlgorithm = tokenBuilder().expiration(java.util.Date.from(now.plusSeconds(300)))
                .signWith(io.jsonwebtoken.Jwts.SIG.HS512.key().build()).compact();
        int before = backendRequests.get();
        for (String path : protectedPaths) {
            for (String header : java.util.List.of("Bearer", "Bearer ", "Bearer not-a-jwt", "Bearer a.b.c",
                    "Basic " + java.util.Base64.getEncoder().encodeToString((username + ":" + password)
                            .getBytes(StandardCharsets.UTF_8)),
                    "Bearer " + tampered, "Bearer " + expired, "Bearer " + wrongKey,
                    "Bearer " + wrongIssuer, "Bearer " + wrongAudience, "Bearer " + noExpiry,
                    "Bearer " + noSubject, "Bearer " + unsigned, "Bearer " + future,
                    "Bearer " + wrongAlgorithm)) {
                client.get().uri(path).header("Authorization", header).exchange()
                        .expectStatus().isUnauthorized();
            }
            client.get().uri(path).header("Authorization", "Bearer " + valid, "Bearer " + valid)
                    .exchange().expectStatus().isUnauthorized();
        }
        org.assertj.core.api.Assertions.assertThat(backendRequests.get()).isEqualTo(before);
    }

    private io.jsonwebtoken.JwtBuilder tokenBuilder() {
        return io.jsonwebtoken.Jwts.builder().subject(username).issuer(issuer).audience().add(audience).and();
    }

    @Test
    void authenticationIsNotReusedWithoutBearerToken() {
        String token = login();
        client.get().uri(protectedPaths[0]).header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isOk().expectHeader().doesNotExist("Set-Cookie");
        client.get().uri(protectedPaths[0]).exchange().expectStatus().isUnauthorized();
    }

    @Test
    void healthAndLoginStayPublicEvenWithAnInvalidBearerHeader() {
        for (String path : new String[]{"/", "/login", "/actuator/health"}) {
            client.get().uri(path).exchange().expectStatus().isOk();
            client.get().uri(path).header("Authorization", "Bearer invalid")
                    .exchange().expectStatus().isOk();
        }
        client.get().uri("/actuator/health").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.components").doesNotExist();
        client.post().uri("/auth/login").header("Authorization", "Bearer invalid")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(java.util.Map.of("username", username, "password", password))
                .exchange().expectStatus().isOk();
    }

    @Test
    void malformedLoginBodyReturns400() {
        client.post().uri("/auth/login").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(java.util.Map.of("username", username)).exchange().expectStatus().isBadRequest();
        client.post().uri("/auth/login").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{").exchange().expectStatus().isBadRequest();
    }
}
