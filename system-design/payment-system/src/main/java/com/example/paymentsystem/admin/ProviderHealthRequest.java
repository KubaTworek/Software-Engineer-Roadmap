package com.example.paymentsystem.admin;

import com.example.paymentsystem.psp.ProviderHealthStatus;

public record ProviderHealthRequest(ProviderHealthStatus status) {
}
