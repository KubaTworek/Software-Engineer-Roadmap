package com.example.ecommerce.invoice.dto;

import java.math.BigDecimal;
import java.time.Instant;

public final class InvoiceDtos {
    private InvoiceDtos() {}

    public record InvoiceResponse(
            Long id,
            Long orderId,
            String invoiceNumber,
            BigDecimal netAmount,
            BigDecimal vatAmount,
            BigDecimal grossAmount,
            String currency,
            Instant issuedAt
    ) {}

    public record InvoiceDocumentResponse(
            String invoiceNumber,
            String html
    ) {}
}
