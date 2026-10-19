package pl.jakubtworek.backend_engineering.stage_1.block_c.jpa;

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
    public List<UserListItem> findUsersByLastName(String lastName) {
        return userRepository.findByLastName(lastName).stream()
                .map(UserListItem::from)
                .toList();
    }

    /**
     * Standard business transaction.
     */
    @Transactional
    public UserListItem createUser(User user) {
        return UserListItem.from(userRepository.save(user));
    }

    /**
     * Demonstrates pagination and sorting.
     */
    @Transactional(readOnly = true)
    public Page<UserListItem> getUsersPage(Pageable requestedPage) {
        int boundedSize = Math.min(requestedPage.getPageSize(), 100);
        Pageable pageable = PageRequest.of(
                requestedPage.getPageNumber(),
                boundedSize,
                requestedPage.getSort().isSorted()
                        ? requestedPage.getSort()
                        : Sort.by("lastName", "id").ascending()
        );
        return userRepository.findAll(pageable).map(UserListItem::from);
    }

    /**
     * Demonstrates query derivation.
     */
    @Transactional(readOnly = true)
    public List<UserListItem> findAdults() {
        return userRepository.findByAgeGreaterThan(18).stream()
                .map(UserListItem::from)
                .toList();
    }

    /**
     * Demonstrates custom JPQL query.
     */
    @Transactional(readOnly = true)
    public List<UserListItem> findOlderThan(int age) {
        return userRepository.findOlderThan(age).stream()
                .map(UserListItem::from)
                .toList();
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
    public List<UserOrderSummary> findOrderSummariesNaively() {
        return userRepository.findAll().stream()
                .map(UserOrderSummary::from)
                .toList();
    }

    /**
     * Demonstrates solution using JOIN FETCH.
     */
    @Transactional(readOnly = true)
    public List<UserOrderSummary> findOrderSummariesWithFetchJoin() {
        return userRepository.findAllWithOrders().stream()
                .map(UserOrderSummary::from)
                .toList();
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

    /** Cursor pagination avoids OFFSET and a full count query. */
    @Transactional(readOnly = true)
    public UserCursorPage getNextUsers(UserCursor cursor, int requestedSize) {
        if (requestedSize <= 0 || requestedSize > 100) {
            throw new IllegalArgumentException("page size must be between 1 and 100");
        }

        String lastName = cursor == null ? null : cursor.lastName();
        Long id = cursor == null ? null : cursor.id();
        Slice<User> slice = userRepository.findNextSlice(
                lastName,
                id,
                PageRequest.of(0, requestedSize)
        );
        List<UserListItem> items = slice.getContent().stream()
                .map(UserListItem::from)
                .toList();
        UserCursor nextCursor = slice.hasNext() && !items.isEmpty()
                ? toCursor(items.get(items.size() - 1))
                : null;
        return new UserCursorPage(items, nextCursor);
    }

    private static UserCursor toCursor(UserListItem item) {
        return new UserCursor(item.lastName(), item.id());
    }
}
