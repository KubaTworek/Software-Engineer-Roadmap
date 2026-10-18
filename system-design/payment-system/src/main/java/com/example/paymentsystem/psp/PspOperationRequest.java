package com.example.paymentsystem.psp;

import com.example.paymentsystem.operation.ProviderOperationType;

import java.util.UUID;

/** Kanoniczna komenda dla efektu następującego po utworzeniu płatności. */
public record PspOperationRequest(UUID operationId, UUID paymentId, String providerPaymentId,
                                  ProviderOperationType type, long amount, String currency,
                                  String idempotencyKey) { }
