package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.coroutines.async.kotlin

// UserApiClient simulates an external API client.
class UserApiClient {

    suspend fun fetchUserById(userId: String): User {
        // suspend means this function can pause without blocking a thread.
        return User(userId, "John Doe")
    }
}