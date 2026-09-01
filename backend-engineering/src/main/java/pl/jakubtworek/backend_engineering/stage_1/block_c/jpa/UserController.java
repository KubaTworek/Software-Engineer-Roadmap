package pl.jakubtworek.backend_engineering.stage_1.block_c.jpa;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * REST controller exposing repository features.
 */
@RestController("userJpaController")
@RequestMapping("/users")
@Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Demonstrates automatic Pageable binding.
     *
     * Example request:
     * GET /users/page?page=0&size=10&sort=lastName,asc
     */
    @GetMapping("/page")
    public Page<UserListItem> getUsersPage(
            @PageableDefault(size = 20)
            Pageable pageable
    ) {
        return userService.getUsersPage(pageable);
    }

    /**
     * Seek pagination is a better fit for an infinite scroll or a next-page API.
     * Both cursor fields are required because together they define a unique position.
     */
    @GetMapping("/cursor")
    public UserCursorPage getUsersCursor(
            @RequestParam(required = false) @Size(min = 1) String afterLastName,
            @RequestParam(required = false) @Positive Long afterId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        if ((afterLastName == null) != (afterId == null)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "afterLastName and afterId must be provided together"
            );
        }
        UserCursor cursor = afterLastName == null ? null : new UserCursor(afterLastName, afterId);
        return userService.getNextUsers(cursor, size);
    }

    /**
     * Demonstrates query derivation.
     */
    @GetMapping("/lastname/{lastName}")
    public List<UserListItem> findByLastName(
            @PathVariable String lastName
    ) {
        return userService.findUsersByLastName(lastName);
    }

    /**
     * Demonstrates projection usage.
     */
    @GetMapping("/names")
    public List<UserNameProjection> namesOnly() {
        return userService.getUserNamesOnly();
    }

    /**
     * Demonstrates JOIN FETCH usage.
     */
    @GetMapping("/with-orders")
    public List<UserOrderSummary> usersWithOrders() {
        return userService.findOrderSummariesWithFetchJoin();
    }
}
