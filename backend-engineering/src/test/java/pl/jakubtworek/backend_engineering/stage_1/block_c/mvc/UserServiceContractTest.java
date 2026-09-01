package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceContractTest {

    private final UserService userService = new UserService();

    @Test
    void shouldReplayTheResultForTheSameIdempotencyKeyAndPayload() {
        CreateUserRequest request = new CreateUserRequest("alice", "alice@example.com");

        UserCreation first = userService.createUser("request-123", request);
        UserCreation replay = userService.createUser("request-123", request);

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.user()).isEqualTo(first.user());
    }

    @Test
    void shouldRejectReusingTheKeyForAnotherOperation() {
        userService.createUser(
                "request-123",
                new CreateUserRequest("alice", "alice@example.com")
        );

        assertThatThrownBy(() -> userService.createUser(
                "request-123",
                new CreateUserRequest("bob", "bob@example.com")
        )).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void shouldExecuteConcurrentRetriesOnlyOnce() throws Exception {
        CreateUserRequest request = new CreateUserRequest("alice", "alice@example.com");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<UserCreation> retry = () -> {
            ready.countDown();
            start.await();
            return userService.createUser("concurrent-request", request);
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(retry);
            var second = executor.submit(retry);
            ready.await();
            start.countDown();

            List<UserCreation> results = List.of(first.get(), second.get());
            assertThat(results).extracting(result -> result.user().id()).containsOnly(1L);
            assertThat(results).extracting(UserCreation::replayed)
                    .containsExactlyInAnyOrder(false, true);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldPreventALostUpdateUsingTheExpectedVersion() {
        UserResponse created = userService.createUser(
                "request-123",
                new CreateUserRequest("alice", "alice@example.com")
        ).user();

        UserResponse updated = userService.replaceUser(
                created.id(),
                0,
                new UpdateUserRequest("alice-new", "new@example.com")
        );

        assertThat(updated.version()).isEqualTo(1);
        assertThatThrownBy(() -> userService.replaceUser(
                created.id(),
                0,
                new UpdateUserRequest("stale", "stale@example.com")
        )).isInstanceOf(PreconditionFailedException.class);
        assertThat(userService.getUser(created.id())).isEqualTo(updated);
    }

    @Test
    void shouldAcceptOnlyAStrongNumericEntityTag() {
        assertThat(EntityVersion.parseStrongEntityTag("\"42\"").value()).isEqualTo(42);
        assertThatThrownBy(() -> EntityVersion.parseStrongEntityTag(null))
                .isInstanceOf(PreconditionRequiredException.class);
        assertThatThrownBy(() -> EntityVersion.parseStrongEntityTag("W/\"42\""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
