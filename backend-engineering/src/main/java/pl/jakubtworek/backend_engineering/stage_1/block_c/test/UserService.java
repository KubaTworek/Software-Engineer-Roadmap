package pl.jakubtworek.backend_engineering.stage_1.block_c.test;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service layer used in tests.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Returns single user or throws exception.
     */
    public User getUser(Long id) {

        return userRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found")
                );
    }

    /**
     * Saves new user.
     */
    public User createUser(String name) {

        return userRepository.save(new User(name));
    }

    /**
     * Returns all users.
     */
    public List<User> getUsers() {
        return userRepository.findAll();
    }
}