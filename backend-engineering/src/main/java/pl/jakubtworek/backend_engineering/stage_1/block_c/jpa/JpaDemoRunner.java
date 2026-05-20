package pl.jakubtworek.backend_engineering.stage_1.block_c.jpa;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Demonstrates repository and transaction behavior.
 */
@Component
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
        userService.demonstrateNPlusOneProblem();

        /**
         * Demonstrates optimized query.
         */
        userService.solveNPlusOneWithJoinFetch();
    }
}