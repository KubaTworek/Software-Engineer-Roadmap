// src/test/java/com/example/registration/integration/RegisterUserIntegrationTest.java
package pl.jakubtworek.backend_engineering.stage_1.block_e.refactored;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import pl.jakubtworek.backend_engineering.stage_1.block_e.refactored.RegisterUserInput;
import pl.jakubtworek.backend_engineering.stage_1.block_e.refactored.RegisterUserOutput;
import pl.jakubtworek.backend_engineering.stage_1.block_e.refactored.RegisterUserUseCase;
import pl.jakubtworek.backend_engineering.stage_1.block_e.refactored.SpringDataUserJpaRepository;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase
class RegisterUserIntegrationTest {

    @Autowired
    private RegisterUserUseCase useCase;

    @Autowired
    private SpringDataUserJpaRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void shouldPersistUserInTestDatabase() {
        RegisterUserOutput output =
                useCase.handle(new RegisterUserInput("anna", "anna@example.com"));

        assertNotNull(output.userId());
        assertTrue(repository.existsByEmail("anna@example.com"));
    }
}
