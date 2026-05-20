package pl.jakubtworek.backend_engineering.stage_1.block_e.legacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.jakubtworek.backend_engineering.stage_1.block_e.legacy.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureTestDatabase
class LegacyUserServiceCharacterizationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private LegacyUserJpaRepository repository;

    @MockitoBean
    private LegacyEmailService emailService;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        Mockito.reset(emailService);
    }

    @Test
    void shouldPersistUserAndSendMailForValidInput() {
        LegacyUserEntity result =
                userService.registerUser(new UserDto("alice", "alice@example.com"), true);

        assertNotNull(result);
        assertNotNull(result.getId());
        assertTrue(repository.existsByEmail("alice@example.com"));
        verify(emailService).sendWelcome("alice@example.com");
    }

    @Test
    void shouldReturnNullForInvalidEmailAndNotPersist() {
        LegacyUserEntity result =
                userService.registerUser(new UserDto("bob", "wrong-email"), true);

        assertNull(result);
        assertEquals(0, repository.count());
        verifyNoInteractions(emailService);
    }
}
