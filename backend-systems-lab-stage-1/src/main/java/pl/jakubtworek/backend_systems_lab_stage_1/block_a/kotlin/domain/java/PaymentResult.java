package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.domain.java;

// Java sealed interface restricts which classes can implement it.
// This is useful for modeling a closed set of domain events.
public sealed interface PaymentResult
        permits PaymentSuccess, PaymentFailure {
}