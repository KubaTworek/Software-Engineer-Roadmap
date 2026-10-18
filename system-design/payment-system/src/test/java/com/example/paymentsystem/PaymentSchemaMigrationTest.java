package com.example.paymentsystem;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Loading this context proves that Flyway can install a schema accepted by Hibernate. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:payment-migration;MODE=PostgreSQL")
class PaymentSchemaMigrationTest {
    @Test
    void applicationStartsFromAnEmptyDatabase() { }
}
