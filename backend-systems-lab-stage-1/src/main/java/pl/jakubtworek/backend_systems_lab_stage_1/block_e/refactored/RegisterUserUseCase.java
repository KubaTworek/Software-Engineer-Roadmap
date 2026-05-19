package pl.jakubtworek.backend_systems_lab_stage_1.block_e.refactored;

import java.time.Clock;
import java.time.LocalDate;

public final class RegisterUserUseCase {

    private final UserRepositoryPort userRepository;
    private final EmailServicePort emailService;
    private final Clock clock;

    public RegisterUserUseCase(
            UserRepositoryPort userRepository,
            EmailServicePort emailService,
            Clock clock) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.clock = clock;
    }

    public RegisterUserOutput handle(RegisterUserInput input) {
        validate(input);

        if (userRepository.existsByEmail(input.email())) {
            throw new DuplicateEmailException("email already exists");
        }

        User newUser = User.newUser(
                input.username(),
                input.email(),
                LocalDate.now(clock));

        User saved = userRepository.save(newUser);
        emailService.sendWelcomeEmail(saved.getEmail());

        return new RegisterUserOutput(
                saved.getId(),
                saved.getUsername(),
                saved.getEmail(),
                saved.getRegisteredAt());
    }

    private void validate(RegisterUserInput input) {
        if (input.username() == null || input.username().isBlank()) {
            throw new ValidationException("username is required");
        }
        if (input.email() == null || !input.email().contains("@")) {
            throw new ValidationException("email is invalid");
        }
    }
}
