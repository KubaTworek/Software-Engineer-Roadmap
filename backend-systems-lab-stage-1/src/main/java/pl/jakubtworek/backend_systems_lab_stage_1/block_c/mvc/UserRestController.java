package pl.jakubtworek.backend_systems_lab_stage_1.block_c.mvc;

import demo.mvc.auth.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller.
 *
 * DispatcherServlet finds this controller method
 * using HandlerMapping based on:
 * - URL path,
 * - HTTP method,
 * - request mapping annotations.
 */
@RestController
@RequestMapping("/api/users")
public class UserRestController {

    private final UserService userService;

    public UserRestController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Demonstrates @PathVariable.
     *
     * PathVariableMethodArgumentResolver extracts {id}
     * from request path and converts it to Long.
     */
    @GetMapping("/{id}")
    public UserResponse getUser(
            @PathVariable @Min(1) Long id
    ) {
        return userService.getUser(id);
    }

    /**
     * Demonstrates @RequestParam.
     *
     * RequestParamMethodArgumentResolver reads query parameter
     * from URL, for example:
     *
     * GET /api/users/search?email=test@example.com
     */
    @GetMapping("/search")
    public ResponseEntity<String> searchUser(
            @RequestParam String email
    ) {
        return ResponseEntity.ok("Searching user by email: " + email);
    }

    /**
     * Demonstrates @RequestBody + @Valid.
     *
     * RequestResponseBodyMethodProcessor:
     * - reads JSON body,
     * - uses HttpMessageConverter to create DTO,
     * - triggers validation because of @Valid.
     *
     * If validation fails, Spring throws MethodArgumentNotValidException.
     */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request
    ) {
        UserResponse response = userService.createUser(request);

        return ResponseEntity.ok(response);
    }

    /**
     * Demonstrates custom HandlerMethodArgumentResolver.
     *
     * AuthUser is not read from request body or path.
     * It is resolved by custom AuthUserArgumentResolver.
     */
    @GetMapping("/me")
    public ResponseEntity<String> currentUser(
            AuthUser authUser
    ) {
        return ResponseEntity.ok(
                "Current authenticated user: " + authUser.username()
        );
    }
}