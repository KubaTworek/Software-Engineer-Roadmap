package pl.jakubtworek.backend_systems_lab_stage_1.block_a.completable_future;

import java.util.concurrent.ThreadLocalRandom;

public class ServiceFetcher {

    public static User fetchUser(int id) {
        sleepRandom();
        return new User(id, "User-" + id);
    }

    public static Orders fetchOrders(int id) {
        sleepRandom();
        return new Orders(id, 3);
    }

    public static Payments fetchPayments(int id) {
        sleepRandom();
        return new Payments(id, true);
    }

    public static String fetchSlowService() {
        sleep(3000);
        return "slow-data";
    }

    public static String fetchFailingService() {
        throw new RuntimeException("Downstream failure");
    }

    private static void sleepRandom() {
        sleep(ThreadLocalRandom.current().nextInt(100, 300));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }
}