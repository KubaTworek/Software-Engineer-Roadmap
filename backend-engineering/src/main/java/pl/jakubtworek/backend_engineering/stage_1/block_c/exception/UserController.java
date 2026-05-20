package pl.jakubtworek.backend_engineering.stage_1.block_c.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller demonstrating validation flow.
 */
@RestController("UserExceptionController")
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * @Valid triggers validation of request body.
     *
     * If validation fails and BindingResult is not present,
     * Spring throws MethodArgumentNotValidException.
     *
     * That exception is handled globally by GlobalExceptionHandler.
     */
    @PostMapping
    public UserResponse createUser(
            @Valid @RequestBody CreateUserRequest request
    ) {
        return userService.createUser(request);
    }

    /**
     * Demonstrates manual validation handling with BindingResult.
     *
     * This approach is possible, but global exception handling
     * is usually cleaner and more consistent.
     */
    @PostMapping("/manual-validation")
    public UserResponse createUserManually(
            @Valid @RequestBody CreateUserRequest request,
            BindingResult bindingResult
    ) {
        if (bindingResult.hasErrors()) {
            /**
             * In real code, convert errors to ApiError.
             * This example keeps it simple.
             */
            throw new IllegalArgumentException("Manual validation failed");
        }

        return userService.createUser(request);
    }

    /**
     * @Min validates path variable.
     *
     * For this to work, controller class or method validation
     * must be enabled depending on Spring version/configuration.
     */
    @GetMapping("/{id}")
    public UserResponse getUser(
            @PathVariable @Min(value = 1, message = "Id must be positive") Long id
    ) {
        return userService.getUser(id);
    }
}