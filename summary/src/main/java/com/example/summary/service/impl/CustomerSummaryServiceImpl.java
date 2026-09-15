package com.example.summary.service.impl;

import com.example.summary.client.AccountsFeignClient;
import com.example.summary.client.CardsFeignClient;
import com.example.summary.client.LoansFeignClient;
import com.example.summary.dto.*;
import com.example.summary.exception.DownstreamServiceException;
import com.example.summary.exception.ResourceNotFoundException;
import com.example.summary.service.ICustomerSummaryService;
import feign.FeignException;
import feign.RetryableException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.util.function.Supplier;

@Service
public class CustomerSummaryServiceImpl implements ICustomerSummaryService {
    private final AccountsFeignClient accountsClient;
    private final CardsFeignClient cardsClient;
    private final LoansFeignClient loansClient;

    public CustomerSummaryServiceImpl(AccountsFeignClient accountsClient,
            CardsFeignClient cardsClient, LoansFeignClient loansClient) {
        this.accountsClient = accountsClient;
        this.cardsClient = cardsClient;
        this.loansClient = loansClient;
    }

    @Override
    public CustomerSummaryDto fetchCustomerSummary(String mobileNumber) {
        AccountsCustomerResponseDto accounts = fetch("Accounts", false,
                () -> accountsClient.fetchAccount(mobileNumber));
        if (accounts.accountsDto() == null || accounts.name() == null
                || accounts.email() == null || accounts.mobileNumber() == null) {
            throw new DownstreamServiceException(HttpStatus.BAD_GATEWAY,
                    "Accounts returned an incomplete response", null);
        }
        CardsDto card = fetch("Cards", true, () -> cardsClient.fetchCard(mobileNumber));
        LoansDto loan = fetch("Loans", true, () -> loansClient.fetchLoan(mobileNumber));
        return new CustomerSummaryDto(
                new CustomerDto(accounts.name(), accounts.email(), accounts.mobileNumber()),
                accounts.accountsDto(), card, loan);
    }

    private <T> T fetch(String service, boolean optional, Supplier<T> request) {
        try {
            T response = request.get();
            if (response == null) {
                throw new DownstreamServiceException(HttpStatus.BAD_GATEWAY,
                        service + " returned an empty response", null);
            }
            return response;
        } catch (FeignException.NotFound exception) {
            // Only a not-found response may represent an absent optional product.
            if (optional) {
                return null;
            }
            throw new ResourceNotFoundException("Customer or account not found");
        } catch (RetryableException exception) {
            if (hasTimeoutCause(exception)) {
                throw new DownstreamServiceException(HttpStatus.GATEWAY_TIMEOUT,
                        service + " request timed out", exception);
            }
            // A response with Retry-After is still an HTTP failure, not a connection failure.
            HttpStatus status = exception.status() > 0
                    ? HttpStatus.BAD_GATEWAY : HttpStatus.SERVICE_UNAVAILABLE;
            throw new DownstreamServiceException(status, service + " is unavailable", exception);
        } catch (FeignException exception) {
            throw new DownstreamServiceException(HttpStatus.BAD_GATEWAY,
                    service + " returned an invalid or unsuccessful response", exception);
        }
    }

    private boolean hasTimeoutCause(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}