package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

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
@Validated
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
    public ResponseEntity<UserResponse> getUser(
            @PathVariable @Min(1) Long id
    ) {
        UserResponse user = userService.getUser(id);
        return versionedResponse(user);
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
    public ResponseEntity<UserResponse> searchUser(
            @RequestParam @Email String email
    ) {
        return versionedResponse(userService.findByEmail(email));
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
            @RequestHeader("Idempotency-Key")
            @Size(min = 8, max = 128)
            String idempotencyKey,
            @Valid @RequestBody CreateUserRequest request
    ) {
        UserCreation creation = userService.createUser(idempotencyKey, request);
        UserResponse user = creation.user();

        return ResponseEntity.created(URI.create("/api/users/" + user.id()))
                .eTag(new EntityVersion(user.version()).toEntityTag())
                .header("Idempotency-Replayed", Boolean.toString(creation.replayed()))
                .body(user);
    }

    /**
     * PUT replaces the complete writable representation. If-Match prevents a
     * client working on an old representation from overwriting a newer update.
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> replaceUser(
            @PathVariable @Min(1) Long id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        EntityVersion expectedVersion = EntityVersion.parseStrongEntityTag(ifMatch);
        UserResponse updated = userService.replaceUser(id, expectedVersion.value(), request);
        return versionedResponse(updated);
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

    private static ResponseEntity<UserResponse> versionedResponse(UserResponse user) {
        return ResponseEntity.ok()
                .eTag(new EntityVersion(user.version()).toEntityTag())
                .body(user);
    }
}
