package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.coroutines.async.kotlin

// Kotlin async code is commonly written with suspend functions
// and coroutines instead of CompletableFuture chains.
class UserService(
    private val apiClient: UserApiClient
) {

    suspend fun getUser(userId: String): User {
        // This looks like normal sequential code,
        // but it can suspend without blocking the thread.
        return apiClient.fetchUserById(userId)
    }

    suspend fun getUserName(userId: String): String {
        // No thenApply is needed.
        // The result is handled like a regular value.
        val user = getUser(userId)
        return user.name
    }

    suspend fun getUserProfile(userId: String): UserProfile {
        // Sequential-looking async flow.
        val user = getUser(userId)
        return UserProfile(user, "STANDARD")
    }
}