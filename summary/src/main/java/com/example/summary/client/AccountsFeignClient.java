package com.example.summary.client;

import com.example.summary.dto.AccountsCustomerResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "accounts", url = "${services.accounts.url}")
public interface AccountsFeignClient {
    @GetMapping("/accounts/api/fetch")
    AccountsCustomerResponseDto fetchAccount(@RequestParam("mobileNumber") String mobileNumber);
}