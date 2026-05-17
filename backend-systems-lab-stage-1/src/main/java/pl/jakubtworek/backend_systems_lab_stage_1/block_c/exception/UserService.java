package pl.jakubtworek.backend_systems_lab_stage_1.block_c.exception;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Example service used by REST controller.
 *
 * It throws domain-specific exceptions that are later translated
 * into HTTP responses by GlobalExceptionHandler.
 */
@Service
public class UserService {

    private final Map<Long, UserResponse> users = new ConcurrentHashMap<>();

    /**
     * Creates user or throws business exception.
     */
    public UserResponse createUser(CreateUserRequest request) {

        boolean emailAlreadyExists =
                users.values()
                        .stream()
                        .anyMatch(user -> user.email().equals(request.email()));

        if (emailAlreadyExists) {
            throw new BusinessRuleViolationException(
                    "User with this email already exists"
            );
        }

        Long id = users.size() + 1L;

        UserResponse response = new UserResponse(
                id,
                request.username(),
                request.email()
        );

        users.put(id, response);

        return response;
    }

    /**
     * Returns user or throws ResourceNotFoundException.
     */
    public UserResponse getUser(Long id) {

        UserResponse user = users.get(id);

        if (user == null) {
            throw new ResourceNotFoundException(
                    "User with id " + id + " was not found"
            );
        }

        return user;
    }
}