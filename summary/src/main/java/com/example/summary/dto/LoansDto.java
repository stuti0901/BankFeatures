package com.example.summary.dto;

public record LoansDto(String mobileNumber, String loanNumber, String loanType,
        int totalLoan, int amountPaid, int outstandingAmount) {
}