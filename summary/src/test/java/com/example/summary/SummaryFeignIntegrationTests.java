package com.example.summary;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SummaryFeignIntegrationTests {
    private static final String MOBILE = "9876543210";
    private static final String ENDPOINT = "/summary/api/customer";
    private static final List<String> requests = new CopyOnWriteArrayList<>();
    private static final Stub accounts = new Stub("accounts");
    private static final Stub cards = new Stub("cards");
    private static final Stub loans = new Stub("loans");
    private static final String ACCOUNT_JSON = """
            {"name":"Sample Customer","email":"sample@example.test","mobileNumber":"9876543210",
             "accountsDto":{"accountNumber":3454433243,"accountType":"Savings","branchAddress":"Main Branch"}}
            """;
    private static final String CARD_JSON = """
            {"mobileNumber":"9876543210","cardNumber":"100646930341","cardType":"Credit Card",
             "totalLimit":100000,"amountUsed":1000,"availableAmount":99000}
            """;
    private static final String LOAN_JSON = """
            {"mobileNumber":"9876543210","loanNumber":"LN12345","loanType":"Home Loan",
             "totalLoan":100000,"amountPaid":1000,"outstandingAmount":99000}
            """;

    @Autowired MockMvc mvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("ACCOUNTS_SERVICE_URL", accounts::url);
        registry.add("CARDS_SERVICE_URL", cards::url);
        registry.add("LOANS_SERVICE_URL", loans::url);
        registry.add("FEIGN_READ_TIMEOUT", () -> 300);
        registry.add("FEIGN_CONNECT_TIMEOUT", () -> 300);
    }

    @BeforeEach
    void reset() {
        requests.clear();
        accounts.reset(ACCOUNT_JSON);
        cards.reset(CARD_JSON);
        loans.reset(LOAN_JSON);
    }

    @AfterAll
    static void stop() {
        accounts.close();
        cards.close();
        loans.close();
    }

    @Test
    void completeSummaryUsesRealFeignClientsAndExactSequentialPathsAndQueries() throws Exception {
        mvc.perform(get(ENDPOINT).param("mobileNumber", MOBILE))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"customer":{"name":"Sample Customer","email":"sample@example.test","mobileNumber":"9876543210"},
                         "account":{"accountNumber":3454433243,"accountType":"Savings","branchAddress":"Main Branch"},
                         "card":{"mobileNumber":"9876543210","cardNumber":"100646930341","cardType":"Credit Card",
                                 "totalLimit":100000,"amountUsed":1000,"availableAmount":99000},
                         "loan":{"mobileNumber":"9876543210","loanNumber":"LN12345","loanType":"Home Loan",
                                 "totalLoan":100000,"amountPaid":1000,"outstandingAmount":99000}}
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
        assertThat(requests).containsExactly(
                "accounts:GET:/accounts/api/fetch?mobileNumber=" + MOBILE,
                "cards:GET:/api/fetch?mobileNumber=" + MOBILE,
                "loans:GET:/api/fetch?mobileNumber=" + MOBILE);
    }

    @Test
    void cardMissing() throws Exception {
        cards.status = 404;
        mvc.perform(get(ENDPOINT).param("mobileNumber", MOBILE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.card").value(nullValue()))
                .andExpect(jsonPath("$.loan.loanNumber").value("LN12345"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"card\":null")));
    }

    @Test
    void loanMissing() throws Exception {
        loans.status = 404;
        mvc.perform(get(ENDPOINT).param("mobileNumber", MOBILE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loan").value(nullValue()))
                .andExpect(jsonPath("$.card.cardNumber").value("100646930341"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"loan\":null")));
    }

    @Test
    void bothOptionalProductsMissing() throws Exception {
        cards.status = loans.status = 404;
        mvc.perform(get(ENDPOINT).param("mobileNumber", MOBILE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.mobileNumber").value(MOBILE))
                .andExpect(jsonPath("$.account.accountNumber").value(3454433243L))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"card\":null")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"loan\":null")));
        assertThat(requests).hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Customer", "Account"})
    void missingCustomerOrAccountStopsBeforeOptionalCalls(String resource) throws Exception {
        accounts.status = 404;
        accounts.body = "{\"errorCode\":\"NOT_FOUND\",\"errorMessage\":\"" + resource + " not found\"}";
        assertError(404, "NOT_FOUND");
        assertThat(requests).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "123", "12345678901", "abcdefghij", "98765 3210", "+987654321", "９８７６５４３２１０"})
    void invalidMobileNumberMakesNoDownstreamRequests(String mobile) throws Exception {
        mvc.perform(get(ENDPOINT).param("mobileNumber", mobile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.apiPath").value(ENDPOINT))
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errorMessage").isNotEmpty())
                .andExpect(jsonPath("$.errorTime").isNotEmpty());
        assertThat(requests).isEmpty();
    }

    @Test
    void missingMobileNumberUsesSameErrorContract() throws Exception {
        mvc.perform(get(ENDPOINT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.apiPath").value(ENDPOINT))
                .andExpect(jsonPath("$.errorMessage").isNotEmpty())
                .andExpect(jsonPath("$.errorTime").isNotEmpty());
        assertThat(requests).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts", "cards", "loans"})
    void downstreamServerFailureIsNotMissingData(String service) throws Exception {
        stub(service).status = 500;
        stub(service).body = "private downstream failure details";
        assertError(502, "BAD_GATEWAY");
        assertThat(requests).hasSize(service.equals("accounts") ? 1 : service.equals("cards") ? 2 : 3);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 429, 503})
    void otherDownstreamHttpFailuresAreNotOptionalAbsence(int status) throws Exception {
        cards.status = status;
        assertError(502, "BAD_GATEWAY");
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts", "cards", "loans"})
    void malformedDownstreamJsonReturns502(String service) throws Exception {
        stub(service).body = "{invalid json";
        assertError(502, "BAD_GATEWAY");
    }

    @ParameterizedTest
    @ValueSource(strings = {"accounts", "cards", "loans"})
    void emptySuccessfulResponseIsNotMissingData(String service) throws Exception {
        stub(service).body = "";
        assertError(502, "BAD_GATEWAY");
    }

    @Test
    void incompleteAccountsSuccessIsAContractFailure() throws Exception {
        accounts.body = "{\"name\":\"Sample Customer\"}";
        assertError(502, "BAD_GATEWAY");
        assertThat(requests).hasSize(1);
    }

    @Test
    void readTimeoutReturns504WithoutRetryOrLoanCall() throws Exception {
        cards.delayMillis = 1200;
        assertError(504, "GATEWAY_TIMEOUT");
        assertThat(requests).hasSize(2);
    }

    private void assertError(int status, String code) throws Exception {
        mvc.perform(get(ENDPOINT).param("mobileNumber", MOBILE))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.apiPath").value(ENDPOINT))
                .andExpect(jsonPath("$.errorCode").value(code))
                .andExpect(jsonPath("$.errorMessage").isNotEmpty())
                .andExpect(jsonPath("$.errorTime").isNotEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private downstream failure details"))));
    }

    private Stub stub(String name) {
        return switch (name) {
            case "accounts" -> accounts;
            case "cards" -> cards;
            default -> loans;
        };
    }

    private static class Stub implements AutoCloseable {
        private final HttpServer server;
        private final java.util.concurrent.ExecutorService executor = Executors.newCachedThreadPool();
        volatile int status;
        volatile String body;
        volatile long delayMillis;

        Stub(String name) {
            try {
                server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
                server.setExecutor(executor);
                server.createContext("/", exchange -> {
                    requests.add(name + ":" + exchange.getRequestMethod() + ":" + exchange.getRequestURI());
                    int responseStatus = status;
                    byte[] responseBody = body.getBytes(StandardCharsets.UTF_8);
                    long delay = delayMillis;
                    try {
                        if (delay > 0) Thread.sleep(delay);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(responseStatus, responseBody.length);
                        exchange.getResponseBody().write(responseBody);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        exchange.close();
                    }
                });
                server.start();
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        String url() {
            return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
        }

        void reset(String json) {
            status = 200;
            body = json;
            delayMillis = 0;
        }

        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}