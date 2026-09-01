package pl.jakubtworek.backend_engineering.stage_1.block_c.jpa;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Demonstrates repository and transaction behavior.
 */
@Component
@Profile("demo")
public class JpaDemoRunner implements CommandLineRunner {

    private final UserService userService;

    public JpaDemoRunner(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void run(String... args) {

        /**
         * Demonstrates N+1 problem.
         */
        userService.findOrderSummariesNaively();

        /**
         * Demonstrates optimized query.
         */
        userService.findOrderSummariesWithFetchJoin();
    }
}
