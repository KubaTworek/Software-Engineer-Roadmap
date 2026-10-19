package pl.jakubtworek.backend_engineering.stage_1.block_e.legacy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Intentionally problematic baseline for the refactoring exercise.
 * Returning null, field injection, static time and mixed side effects are kept
 * here so characterization tests can contrast it with the refactored use case.
 */
@Service
public class UserService {

    @Autowired
    private LegacyUserJpaRepository repository;

    @Autowired
    private LegacyEmailService emailService;

    public LegacyUserEntity registerUser(UserDto dto, boolean sendWelcomeEmail) {
        if (dto.username() == null || dto.username().isBlank()) {
            return null;
        }
        if (dto.email() == null || !dto.email().contains("@")) {
            return null;
        }
        if (repository.existsByEmail(dto.email())) {
            return null;
        }

        LegacyUserEntity entity = new LegacyUserEntity();
        entity.setUsername(dto.username());
        entity.setEmail(dto.email());
        entity.setRegisteredAt(LocalDate.now());

        LegacyUserEntity saved = repository.save(entity);

        if (sendWelcomeEmail) {
            emailService.sendWelcome(saved.getEmail());
        }
        return saved;
    }
}
