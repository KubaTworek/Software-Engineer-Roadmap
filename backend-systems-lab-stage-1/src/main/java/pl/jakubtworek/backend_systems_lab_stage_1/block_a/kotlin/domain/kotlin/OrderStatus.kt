package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.domain.kotlin

// enum class is used when states do not need individual data.
enum class OrderStatus {
    CREATED,
    PAID,
    SHIPPED,
    COMPLETED,
    CANCELLED
}