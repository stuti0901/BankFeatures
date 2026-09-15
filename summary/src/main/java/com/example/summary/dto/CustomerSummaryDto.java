package com.example.summary.dto;

@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
public record CustomerSummaryDto(CustomerDto customer, AccountsDto account, CardsDto card, LoansDto loan) {
}