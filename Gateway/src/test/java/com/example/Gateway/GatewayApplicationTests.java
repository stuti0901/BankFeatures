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
        client.get().uri("/accounts/api/fetch?mobileNumber=9876543210").exchange()
                .expectStatus().isOk().expectBody(String.class)
                .isEqualTo("accounts:/accounts/api/fetch?mobileNumber=9876543210");
        client.get().uri("/cards/api/fetch?mobileNumber=9876543210").exchange()
                .expectStatus().isOk().expectBody(String.class)
                .isEqualTo("cards:/api/fetch?mobileNumber=9876543210");
        client.get().uri("/loans/api/fetch?mobileNumber=9876543210").exchange()
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
}
