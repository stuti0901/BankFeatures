package com.example.summary.service;

import com.example.summary.dto.CustomerSummaryDto;

public interface ICustomerSummaryService {
    CustomerSummaryDto fetchCustomerSummary(String mobileNumber);
}