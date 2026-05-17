package pl.jakubtworek.backend_systems_lab_stage_1.block_c.test;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller tested with @WebMvcTest.
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Endpoint tested using MockMvc.
     */
    @GetMapping("/{id}")
    public UserResponse getUser(
            @PathVariable Long id
    ) {
        User user = userService.getUser(id);

        return new UserResponse(
                user.getId(),
                user.getName()
        );
    }

    /**
     * Endpoint demonstrating validation testing.
     */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request
    ) {
        User user = userService.createUser(request.name());

        return ResponseEntity.ok(
                new UserResponse(
                        user.getId(),
                        user.getName()
                )
        );
    }
}