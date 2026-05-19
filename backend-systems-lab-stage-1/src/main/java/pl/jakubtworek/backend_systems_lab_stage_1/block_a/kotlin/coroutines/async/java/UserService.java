package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.coroutines.async.java;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

// Java async code is commonly written with CompletableFuture,
// ExecutorService, or platform/virtual threads.
public class UserService {

    private final UserApiClient apiClient;
    private final ExecutorService executorService;

    public UserService(UserApiClient apiClient, ExecutorService executorService) {
        this.apiClient = apiClient;
        this.executorService = executorService;
    }

    public CompletableFuture<User> getUserAsync(String userId) {
        // supplyAsync runs the blocking operation on another thread.
        // The caller receives a CompletableFuture instead of a direct User.
        return CompletableFuture.supplyAsync(
                () -> apiClient.fetchUserById(userId),
                executorService
        );
    }

    public CompletableFuture<String> getUserNameAsync(String userId) {
        // thenApply transforms the result after the async operation completes.
        return getUserAsync(userId)
                .thenApply(User::getName);
    }

    public CompletableFuture<UserProfile> getUserProfileAsync(String userId) {
        // thenCompose is used when another async operation depends
        // on the result of the previous one.
        return getUserAsync(userId)
                .thenCompose(user ->
                        CompletableFuture.supplyAsync(
                                () -> new UserProfile(user, "STANDARD"),
                                executorService
                        )
                );
    }
}