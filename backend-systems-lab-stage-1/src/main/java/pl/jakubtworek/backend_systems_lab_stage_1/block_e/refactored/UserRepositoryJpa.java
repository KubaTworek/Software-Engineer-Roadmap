package pl.jakubtworek.backend_systems_lab_stage_1.block_e.refactored;

import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepositoryJpa implements UserRepositoryPort {

    private final SpringDataUserJpaRepository repository;

    public UserRepositoryJpa(SpringDataUserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = new UserJpaEntity(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRegisteredAt());

        UserJpaEntity saved = repository.save(entity);
        return new User(
                saved.getId(),
                saved.getUsername(),
                saved.getEmail(),
                saved.getRegisteredAt());
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email)
                .map(e -> new User(e.getId(), e.getUsername(), e.getEmail(), e.getRegisteredAt()));
    }
}
