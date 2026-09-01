package pl.jakubtworek.backend_engineering.stage_1.block_c.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.data.jpa.repositories.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
@Import(UserService.class)
class JpaQueryBehaviorTest {

    @TestConfiguration
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    static class RepositoryTestConfig {
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        userRepository.deleteAll();

        User adams = user("Anna", "Adams", "Keyboard", "Mouse");
        User brownOne = user("Barbara", "Brown", "Monitor");
        User brownTwo = user("Bartosz", "Brown", "Dock");
        User clark = user("Celina", "Clark", "Headphones");
        userRepository.saveAllAndFlush(List.of(adams, brownOne, brownTwo, clark));
        entityManager.clear();
        statistics.clear();
    }

    @Test
    void shouldExposeTheNPlusOneCostAndTheFetchJoinAlternative() {
        List<UserOrderSummary> naive = userService.findOrderSummariesNaively();

        assertThat(naive).hasSize(4);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(5);

        entityManager.clear();
        statistics.clear();

        List<UserOrderSummary> fetched = userService.findOrderSummariesWithFetchJoin();

        assertThat(fetched).hasSize(4);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void shouldTraverseStableKeysetPagesWithoutDuplicates() {
        UserCursorPage first = userService.getNextUsers(null, 2);
        UserCursorPage second = userService.getNextUsers(first.nextCursor(), 2);

        assertThat(first.items()).extracting(UserListItem::lastName)
                .containsExactly("Adams", "Brown");
        assertThat(first.nextCursor()).isNotNull();
        assertThat(second.items()).extracting(UserListItem::lastName)
                .containsExactly("Brown", "Clark");
        assertThat(second.nextCursor()).isNull();
        assertThat(List.of(
                first.items().get(0).id(),
                first.items().get(1).id(),
                second.items().get(0).id(),
                second.items().get(1).id()
        )).doesNotHaveDuplicates();
    }

    @Test
    void shouldRejectAnUnboundedCursorPageRequest() {
        assertThatThrownBy(() -> userService.getNextUsers(null, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 100");
    }

    private static User user(String firstName, String lastName, String... products) {
        User user = new User(firstName, lastName, 30);
        for (String product : products) {
            user.addOrder(product);
        }
        return user;
    }
}
