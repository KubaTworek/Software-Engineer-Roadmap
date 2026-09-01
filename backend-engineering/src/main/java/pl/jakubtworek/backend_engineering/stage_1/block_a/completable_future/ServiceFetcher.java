package pl.jakubtworek.backend_engineering.stage_1.block_a.completable_future;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;

public class ServiceFetcher {

    public User fetchUser(int id) {
        sleepRandom();
        return new User(id, "User-" + id);
    }

    public Orders fetchOrders(int id) {
        sleepRandom();
        return new Orders(id, 3);
    }

    public Payments fetchPayments(int id) {
        sleepRandom();
        return new Payments(id, true);
    }

    public String fetchSlowService() {
        sleep(3000);
        return "slow-data";
    }

    public String fetchFailingService() {
        throw new RuntimeException("Downstream failure");
    }

    private void sleepRandom() {
        sleep(ThreadLocalRandom.current().nextInt(100, 300));
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CompletionException("Service call interrupted", exception);
        }
    }
}
