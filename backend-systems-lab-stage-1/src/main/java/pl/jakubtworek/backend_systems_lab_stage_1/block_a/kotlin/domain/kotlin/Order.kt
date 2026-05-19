package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.domain.kotlin

import java.math.BigDecimal

// Order represents a domain object in Kotlin.
// data class automatically generates equals(), hashCode(),
// toString(), copy(), and component functions.
data class Order(
    val id: String,
    val customer: Customer,
    val totalAmount: BigDecimal,
    val status: OrderStatus
) {
    init {
        require(id.isNotBlank()) {
            "Order id cannot be empty"
        }

        require(totalAmount >= BigDecimal.ZERO) {
            "Total amount cannot be negative"
        }
    }

    // Domain behavior can be placed inside the data class.
    fun isCompleted(): Boolean {
        return status == OrderStatus.COMPLETED
    }
}