package com.ridesharing.mvp.user;

import com.ridesharing.mvp.auth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return UserDto.from(principal.user());
    }
}
