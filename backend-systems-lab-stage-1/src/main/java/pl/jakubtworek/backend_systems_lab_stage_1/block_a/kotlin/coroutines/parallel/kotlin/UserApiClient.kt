package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.coroutines.parallel.kotlin

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

// UserApiClient simulates loading user data from an external service.
class UserApiClient {

    suspend fun fetchUserById(userId: String): User {
        return User(userId, "John Doe")
    }
}