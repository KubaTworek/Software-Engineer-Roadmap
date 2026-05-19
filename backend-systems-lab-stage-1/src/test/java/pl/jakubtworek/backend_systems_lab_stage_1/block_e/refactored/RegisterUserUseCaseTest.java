package pl.jakubtworek.backend_systems_lab_stage_1.block_e.refactored;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private EmailServicePort emailService;

    private final Clock fixedClock =
            Clock.fixed(Instant.parse("2026-05-17T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldRegisterUserWithoutSpringContext() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> ((User) invocation.getArgument(0)).withId(1L));

        RegisterUserUseCase useCase =
                new RegisterUserUseCase(userRepository, emailService, fixedClock);

        RegisterUserOutput output =
                useCase.handle(new RegisterUserInput("alice", "alice@example.com"));

        assertEquals(1L, output.userId());
        assertEquals(LocalDate.of(2026, 5, 17), output.registeredAt());
        verify(userRepository).save(any(User.class));
        verify(emailService).sendWelcomeEmail("alice@example.com");
    }

    @Test
    void shouldRejectDuplicateEmail() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        RegisterUserUseCase useCase =
                new RegisterUserUseCase(userRepository, emailService, fixedClock);

        assertThrows(
                DuplicateEmailException.class,
                () -> useCase.handle(new RegisterUserInput("alice", "alice@example.com")));

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(emailService);
    }

    @Test
    void shouldRejectBlankUsername() {
        RegisterUserUseCase useCase =
                new RegisterUserUseCase(userRepository, emailService, fixedClock);

        assertThrows(
                ValidationException.class,
                () -> useCase.handle(new RegisterUserInput(" ", "alice@example.com")));

        verifyNoInteractions(userRepository, emailService);
    }
}
