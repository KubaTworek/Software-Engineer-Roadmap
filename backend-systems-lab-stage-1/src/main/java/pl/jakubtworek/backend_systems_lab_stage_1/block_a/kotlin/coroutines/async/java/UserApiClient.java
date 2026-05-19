package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.coroutines.async.java;

// UserApiClient simulates an external API client.
public class UserApiClient {

    public User fetchUserById(String userId) {
        // This method blocks the current thread while waiting for the response.
        return new User(userId, "John Doe");
    }
}