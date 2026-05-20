package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple service used by controller.
 *
 * This layer contains business logic.
 * Spring MVC controller should delegate work to services.
 */
@Service
public class UserService {

    private final Map<Long, UserResponse> users = new ConcurrentHashMap<>();

    public UserResponse createUser(CreateUserRequest request) {

        Long id = users.size() + 1L;

        UserResponse response = new UserResponse(
                id,
                request.username(),
                request.email()
        );

        users.put(id, response);

        return response;
    }

    public UserResponse getUser(Long id) {

        UserResponse user = users.get(id);

        if (user == null) {
            throw new UserNotFoundException("User with id " + id + " not found");
        }

        return user;
    }
}