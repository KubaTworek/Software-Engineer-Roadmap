package com.example.newsfeed.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse updateCurrentUser(User user, UpdateUserRequest request) {
        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user does not exist."));

        managedUser.updateProfile(request.displayName(), request.bio());
        User savedUser = userRepository.save(managedUser);

        return UserResponse.from(savedUser);
    }
}
