package com.example.summary;

import com.example.summary.client.*;
import com.example.summary.exception.DownstreamServiceException;
import com.example.summary.service.impl.CustomerSummaryServiceImpl;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CustomerSummaryServiceTests {
    @Test
    void connectionFailureReturns503AndStopsAggregation() {
        var accounts = mock(AccountsFeignClient.class);
        var cards = mock(CardsFeignClient.class);
        var loans = mock(LoansFeignClient.class);
        var request = Request.create(Request.HttpMethod.GET, "/accounts/api/fetch",
                Map.of(), null, StandardCharsets.UTF_8, null);
        when(accounts.fetchAccount("9876543210")).thenThrow(new RetryableException(
                -1, "Connection refused", Request.HttpMethod.GET,
                new ConnectException("Connection refused"), (Long) null, request));
        var service = new CustomerSummaryServiceImpl(accounts, cards, loans);
        assertThatThrownBy(() -> service.fetchCustomerSummary("9876543210"))
                .isInstanceOfSatisfying(DownstreamServiceException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getStatus().value())
                                .isEqualTo(503));
        verifyNoInteractions(cards, loans);
    }
}