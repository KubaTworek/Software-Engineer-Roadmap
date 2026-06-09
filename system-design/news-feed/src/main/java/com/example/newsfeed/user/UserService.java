package com.example.newsfeed.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    @Transactional
    public UserResponse updateCurrentUser(User user, UpdateUserRequest request) {
        user.updateProfile(request.displayName(), request.bio());
        return UserResponse.from(user);
    }
}
