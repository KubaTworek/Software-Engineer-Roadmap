package pl.jakubtworek.backend_systems_lab_stage_1.block_c.jpa;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;

import java.util.List;

/**
 * Service layer controls transaction boundaries.
 *
 * Best practice:
 * - repositories focus on database access,
 * - services manage business transactions.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Read-only transaction optimization.
     *
     * Hibernate may skip dirty checking.
     */
    @Transactional(readOnly = true)
    public List<User> findUsersByLastName(String lastName) {
        return userRepository.findByLastName(lastName);
    }

    /**
     * Standard business transaction.
     */
    @Transactional
    public User createUser(User user) {
        return userRepository.save(user);
    }

    /**
     * Demonstrates pagination and sorting.
     */
    @Transactional(readOnly = true)
    public Page<User> getUsersPage() {

        Pageable pageable = PageRequest.of(
                0,
                20,
                Sort.by("lastName").ascending()
        );

        /**
         * Spring Data automatically generates:
         * - paginated SQL query,
         * - count query.
         */
        return userRepository.findAll(pageable);
    }

    /**
     * Demonstrates query derivation.
     */
    @Transactional(readOnly = true)
    public List<User> findAdults() {
        return userRepository.findByAgeGreaterThan(18);
    }

    /**
     * Demonstrates custom JPQL query.
     */
    @Transactional(readOnly = true)
    public List<User> findOlderThan(int age) {
        return userRepository.findOlderThan(age);
    }

    /**
     * Demonstrates N+1 problem.
     *
     * WARNING:
     * This method may execute:
     * - 1 query for users,
     * - N queries for orders.
     */
    @Transactional(readOnly = true)
    public void demonstrateNPlusOneProblem() {

        List<User> users = userRepository.findAll();

        /**
         * Accessing lazy collection triggers additional queries.
         */
        for (User user : users) {
            System.out.println(user.getOrders().size());
        }
    }

    /**
     * Demonstrates solution using JOIN FETCH.
     */
    @Transactional(readOnly = true)
    public void solveNPlusOneWithJoinFetch() {

        List<User> users = userRepository.findAllWithOrders();

        /**
         * Orders already loaded in one query.
         */
        for (User user : users) {
            System.out.println(user.getOrders().size());
        }
    }

    /**
     * Demonstrates projection optimization.
     *
     * Only selected columns are fetched.
     */
    @Transactional(readOnly = true)
    public List<UserNameProjection> getUserNamesOnly() {
        return userRepository.findAllProjectedBy();
    }
}