package com.example.summary.controller;

import com.example.summary.dto.CustomerSummaryDto;
import com.example.summary.service.ICustomerSummaryService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/summary/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class CustomerSummaryController {
    private final ICustomerSummaryService summaryService;

    public CustomerSummaryController(ICustomerSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/customer")
    public CustomerSummaryDto fetchCustomerSummary(
            @RequestParam("mobileNumber")
            @NotBlank(message = "Mobile number is required")
            @Pattern(regexp = "[0-9]{10}", message = "Mobile number must be 10 digits")
            String mobileNumber) {
        return summaryService.fetchCustomerSummary(mobileNumber);
    }
}