package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.coroutines.parallel.kotlin

// Dashboard aggregates data from multiple services.
data class Dashboard(
    val user: User,
    val orders: List<Order>
)