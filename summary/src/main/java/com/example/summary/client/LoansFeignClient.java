package com.example.summary.client;

import com.example.summary.dto.LoansDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "loans", url = "${services.loans.url}")
public interface LoansFeignClient {
    @GetMapping("/api/fetch")
    LoansDto fetchLoan(@RequestParam("mobileNumber") String mobileNumber);
}