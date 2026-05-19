package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.coroutines.parallel.kotlin

// OrderApiClient simulates loading user orders from another service.
class OrderApiClient {

    suspend fun fetchOrdersByUserId(userId: String): List<Order> {
        return listOf(
            Order("ORD-1", 120.0),
            Order("ORD-2", 75.5)
        )
    }
}