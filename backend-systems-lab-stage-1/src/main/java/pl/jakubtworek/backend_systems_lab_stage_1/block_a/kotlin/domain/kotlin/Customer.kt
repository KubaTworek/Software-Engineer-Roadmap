package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.domain.kotlin

// Customer is compact and expressive.
// The domain model is easy to read because properties are declared directly.
data class Customer(
    val id: String,
    val fullName: String
) {
    init {
        require(id.isNotBlank()) {
            "Customer id cannot be empty"
        }

        require(fullName.isNotBlank()) {
            "Customer name cannot be empty"
        }
    }
}