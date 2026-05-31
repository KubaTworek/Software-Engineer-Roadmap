package pl.jakubtworek.booking.integration.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import pl.jakubtworek.booking.dto.EventCreateRequest;
import pl.jakubtworek.booking.dto.EventResponse;
import pl.jakubtworek.booking.dto.ReservationCreateRequest;
import pl.jakubtworek.booking.entity.CapacityPool;
import pl.jakubtworek.booking.repository.CapacityPoolRepository;
import pl.jakubtworek.booking.repository.ReservationRepository;
import pl.jakubtworek.booking.service.EventService;
import pl.jakubtworek.booking.service.concurrency.AtomicSqlReservationService;
import pl.jakubtworek.booking.service.concurrency.NaiveReservationService;
import pl.jakubtworek.booking.service.concurrency.OptimisticLockingReservationService;
import pl.jakubtworek.booking.service.concurrency.PessimisticLockingReservationService;
import pl.jakubtworek.booking.service.concurrency.SynchronizedReservationService;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Sql(statements = {
        "DELETE FROM outbound_messages",
        "DELETE FROM audit_logs",
        "DELETE FROM reservations",
        "DELETE FROM capacity_pools",
        "DELETE FROM refresh_tokens",
        "DELETE FROM app_users",
        "DELETE FROM events",
        "DELETE FROM customers",
        "DELETE FROM organizations"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrencyStage2IntegrationTest {
    private static final int CAPACITY = 10;
    private static final int REQUESTS = 100;
    private static final int THREADS = REQUESTS;

    @Autowired
    EventService eventService;

    @Autowired
    ReservationRepository reservationRepository;

    @Autowired
    CapacityPoolRepository capacityPoolRepository;

    @Autowired
    NaiveReservationService naiveReservationService;

    @Autowired
    SynchronizedReservationService synchronizedReservationService;

    @Autowired
    OptimisticLockingReservationService optimisticLockingReservationService;

    @Autowired
    PessimisticLockingReservationService pessimisticLockingReservationService;

    @Autowired
    AtomicSqlReservationService atomicSqlReservationService;

    @Test
    void naiveImplementationOversellsBecauseCheckThenActIsNotAtomic() throws Exception {
        // given
        UUID eventId = createEvent(CAPACITY);

        // when
        ConcurrentRunResult result = runConcurrently(REQUESTS, index ->
                naiveReservationService.create(eventId, request(index))
        );

        // then
        long reservationCount = reservationRepository.count();
        CapacityPool pool = getPool(eventId);

        assertThat(result.successes()).isGreaterThan(CAPACITY);
        assertThat(reservationCount).isGreaterThan(CAPACITY);
        assertThat(pool.getAvailableCapacity()).isBetween(0, CAPACITY - 1);
    }

    private Stream<Arguments> safeReservationStrategies() {
        return Stream.of(
                Arguments.of(
                        "synchronized inside single JVM",
                        (BiFunction<UUID, Integer, UUID>) (eventId, index) ->
                                synchronizedReservationService.create(eventId, request(index))
                ),
                Arguments.of(
                        "optimistic locking with @Version and retries",
                        (BiFunction<UUID, Integer, UUID>) (eventId, index) ->
                                optimisticLockingReservationService.create(eventId, request(index))
                ),
                Arguments.of(
                        "pessimistic locking with SELECT FOR UPDATE",
                        (BiFunction<UUID, Integer, UUID>) (eventId, index) ->
                                pessimisticLockingReservationService.create(eventId, request(index))
                ),
                Arguments.of(
                        "atomic SQL conditional update",
                        (BiFunction<UUID, Integer, UUID>) (eventId, index) ->
                                atomicSqlReservationService.create(eventId, request(index))
                )
        );
    }

    @ParameterizedTest(name = "{0} prevents overselling")
    @MethodSource("safeReservationStrategies")
    void safeImplementationsPreventOverselling(
            String name,
            BiFunction<UUID, Integer, UUID> createReservation
    ) throws Exception {
        // given
        UUID eventId = createEvent(CAPACITY);

        // when
        ConcurrentRunResult result = runConcurrently(REQUESTS, index ->
                createReservation.apply(eventId, index)
        );

        // then
        assertNoOverselling(eventId, result);
    }

    private void assertNoOverselling(UUID eventId, ConcurrentRunResult result) {
        CapacityPool pool = getPool(eventId);

        assertThat(result.successes()).isEqualTo(CAPACITY);
        assertThat(reservationRepository.count()).isEqualTo(CAPACITY);
        assertThat(pool.getAvailableCapacity()).isZero();
        assertThat(result.failures()).isEqualTo(REQUESTS - CAPACITY);
    }

    private UUID createEvent(int capacity) {
        EventResponse event = eventService.create(new EventCreateRequest(
                "Concurrent Booking Test",
                "Warsaw",
                "education",
                OffsetDateTime.now().plusDays(30),
                capacity
        ));
        return event.id();
    }

    private CapacityPool getPool(UUID eventId) {
        return capacityPoolRepository.findByEventId(eventId).orElseThrow();
    }

    private ReservationCreateRequest request(int index) {
        return new ReservationCreateRequest(
                "Customer " + index,
                "customer-%03d@example.com".formatted(index)
        );
    }

    private ConcurrentRunResult runConcurrently(int requests, ThrowingReservationCall call) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(THREADS, requests));
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < requests; i++) {
            int index = i;
            Callable<Void> task = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent test did not start in time");
                }
                try {
                    call.execute(index);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
                return null;
            };
            futures.add(executor.submit(task));
        }

        assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        for (Future<Void> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        return new ConcurrentRunResult(successes.get(), failures.get());
    }

    @FunctionalInterface
    private interface ThrowingReservationCall {
        UUID execute(int index) throws Exception;
    }

    private record ConcurrentRunResult(int successes, int failures) {
    }
}
