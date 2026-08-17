package com.example.newsfeed.user;

import com.example.newsfeed.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(@CurrentUser User currentUser) {
        return UserResponse.from(currentUser);
    }

    @PatchMapping("/me")
    public UserResponse updateMe(
            @CurrentUser User currentUser,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return userService.updateCurrentUser(currentUser, request);
    }
}
