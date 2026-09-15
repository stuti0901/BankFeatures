package com.example.summary.dto;

public record CardsDto(String mobileNumber, String cardNumber, String cardType,
        int totalLimit, int amountUsed, int availableAmount) {
}