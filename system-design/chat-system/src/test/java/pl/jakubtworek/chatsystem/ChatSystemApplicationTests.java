package pl.jakubtworek.chatsystem;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ChatSystemApplicationTests {
    @Test
    void contextLoadsWithFlywayMigrationsAndJpaValidation() {
        // Context start is the assertion: Flyway must migrate the schema and Hibernate must validate it.
    }
}
