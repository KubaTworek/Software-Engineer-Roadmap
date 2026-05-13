package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.coroutines.parallel.kotlin

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class DashboardService(
    private val userApiClient: UserApiClient,
    private val orderApiClient: OrderApiClient
) {

    suspend fun loadDashboard(userId: String): Dashboard = coroutineScope {
        // async starts a coroutine for loading the user.
        val userDeferred = async {
            userApiClient.fetchUserById(userId)
        }

        // async starts another coroutine for loading orders.
        val ordersDeferred = async {
            orderApiClient.fetchOrdersByUserId(userId)
        }

        // await waits for the results.
        // If one coroutine fails, coroutineScope cancels the others.
        Dashboard(
            user = userDeferred.await(),
            orders = ordersDeferred.await()
        )
    }
}