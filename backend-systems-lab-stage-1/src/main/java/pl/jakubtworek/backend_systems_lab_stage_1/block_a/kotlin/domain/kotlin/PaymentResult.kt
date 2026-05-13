package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.domain.kotlin

import java.time.Instant

// sealed class represents a closed hierarchy.
// Each subtype can carry different data.
sealed class PaymentResult {

    // Success case with transaction-specific data.
    data class Success(
        val transactionId: String,
        val paidAt: Instant
    ) : PaymentResult()

    // Failure case with error-specific data.
    data class Failure(
        val reason: String
    ) : PaymentResult()
}