package pl.jakubtworek.backend_engineering.stage_1.block_c.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing repository features.
 */
@RestController("userJpaController")
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    public UserController(
            UserService userService,
            UserRepository userRepository
    ) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    /**
     * Demonstrates automatic Pageable binding.
     *
     * Example request:
     * GET /users/page?page=0&size=10&sort=lastName,asc
     */
    @GetMapping("/page")
    public Page<User> getUsersPage(
            @PageableDefault(size = 20)
            Pageable pageable
    ) {
        return userRepository.findAll(pageable);
    }

    /**
     * Demonstrates query derivation.
     */
    @GetMapping("/lastname/{lastName}")
    public List<User> findByLastName(
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
    public List<User> usersWithOrders() {
        return userRepository.findAllWithOrders();
    }
}