package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple service used by controller.
 *
 * This layer contains business logic.
 * Spring MVC controller should delegate work to services.
 */
@Service
public class UserService {

    private final Map<Long, UserResponse> users = new ConcurrentHashMap<>();
    private final Map<String, IdempotentCreation> creationsByKey = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong();

    public UserCreation createUser(String idempotencyKey, CreateUserRequest request) {
        AtomicReference<UserCreation> result = new AtomicReference<>();

        creationsByKey.compute(idempotencyKey, (key, existing) -> {
            if (existing != null) {
                if (!existing.request().equals(request)) {
                    throw new IdempotencyConflictException(
                            "Idempotency-Key was already used with a different request"
                    );
                }
                result.set(new UserCreation(existing.response(), true));
                return existing;
            }

            long id = nextId.incrementAndGet();
            UserResponse response = new UserResponse(
                    id,
                    request.username(),
                    request.email(),
                    0
            );
            users.put(id, response);
            result.set(new UserCreation(response, false));
            return new IdempotentCreation(request, response);
        });

        return result.get();
    }

    public UserResponse getUser(Long id) {

        UserResponse user = users.get(id);

        if (user == null) {
            throw new UserNotFoundException("User with id " + id + " not found");
        }

        return user;
    }

    public UserResponse findByEmail(String email) {
        return users.values().stream()
                .filter(user -> user.email().equalsIgnoreCase(email))
                .findFirst()
                .orElseThrow(() -> new UserNotFoundException(
                        "User with email " + email + " not found"
                ));
    }

    public UserResponse replaceUser(Long id, long expectedVersion, UpdateUserRequest request) {
        AtomicReference<UserResponse> result = new AtomicReference<>();
        users.compute(id, (ignored, current) -> {
            if (current == null) {
                throw new UserNotFoundException("User with id " + id + " not found");
            }
            if (current.version() != expectedVersion) {
                throw new PreconditionFailedException(
                        "Resource changed; fetch its current representation and retry"
                );
            }
            UserResponse updated = new UserResponse(
                    current.id(),
                    request.username(),
                    request.email(),
                    current.version() + 1
            );
            result.set(updated);
            return updated;
        });
        return result.get();
    }

    private record IdempotentCreation(CreateUserRequest request, UserResponse response) {
    }
}
