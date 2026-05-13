package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.coroutines.parallel.java;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

// DashboardService combines data from multiple async sources.
public class DashboardService {

    private final UserApiClient userApiClient;
    private final OrderApiClient orderApiClient;
    private final ExecutorService executorService;

    public DashboardService(UserApiClient userApiClient,
                            OrderApiClient orderApiClient,
                            ExecutorService executorService) {

        this.userApiClient = userApiClient;
        this.orderApiClient = orderApiClient;
        this.executorService = executorService;
    }

    public CompletableFuture<Dashboard> loadDashboard(String userId) {

        // Starts loading the user asynchronously.
        CompletableFuture<User> userFuture =
                CompletableFuture.supplyAsync(
                        () -> userApiClient.fetchUserById(userId),
                        executorService
                );

        // Starts loading orders asynchronously.
        CompletableFuture<List<Order>> ordersFuture =
                CompletableFuture.supplyAsync(
                        () -> orderApiClient.fetchOrdersByUserId(userId),
                        executorService
                );

        // thenCombine waits for both async operations to complete
        // and combines the results into a Dashboard object.
        return userFuture.thenCombine(
                ordersFuture,
                Dashboard::new
        );
    }
}