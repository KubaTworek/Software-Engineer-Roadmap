package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.coroutines.parallel.java;

import java.util.concurrent.ExecutorService;

// UserApiClient simulates loading user data from an external service.
public class UserApiClient {

    public User fetchUserById(String userId) {
        return new User(userId, "John Doe");
    }
}