package pl.jakubtworek.chatsystem.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.jakubtworek.chatsystem.auth.UserPrincipal;
import pl.jakubtworek.chatsystem.common.NotFoundException;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        AppUser user = userRepository.findById(principal.id())
                .orElseThrow(() -> new NotFoundException("Current user not found"));
        return UserResponse.from(user);
    }

    @GetMapping("/{id}")
    public PublicUserResponse getUser(@PathVariable UUID id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return PublicUserResponse.from(user);
    }
}
