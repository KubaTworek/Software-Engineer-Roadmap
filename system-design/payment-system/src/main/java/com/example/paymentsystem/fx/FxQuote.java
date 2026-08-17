package com.example.paymentsystem.fx;

public record FxQuote(long sourceAmount, String sourceCurrency, long targetAmount, String targetCurrency, String rate) {
}
