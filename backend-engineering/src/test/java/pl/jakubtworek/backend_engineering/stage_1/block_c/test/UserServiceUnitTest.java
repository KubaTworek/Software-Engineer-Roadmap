package pl.jakubtworek.backend_engineering.stage_1.block_c.test;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit test.
 *
 * No Spring context is started.
 *
 * Advantages:
 * - very fast,
 * - isolated,
 * - simple.
 */
public class UserServiceUnitTest {

    /**
     * Repository is mocked manually using Mockito.
     */
    private final UserRepository userRepository =
            Mockito.mock(UserRepository.class);

    /**
     * Real service with mocked dependency.
     */
    private final UserService userService =
            new UserService(userRepository);

    @Test
    void shouldReturnUser() {

        User user = new User("John");

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        User result = userService.getUser(1L);

        assertThat(result.getName())
                .isEqualTo("John");
        verify(userRepository).findById(1L);
    }

    @Test
    void shouldExposeTheDomainMeaningOfAMissingUser() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(404L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User not found");
    }
}
