package pl.jakubtworek.chatsystem;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.jakubtworek.chatsystem.conversation.*;
import pl.jakubtworek.chatsystem.message.*;
import pl.jakubtworek.chatsystem.outbox.*;
import pl.jakubtworek.chatsystem.support.TestUsers;
import pl.jakubtworek.chatsystem.user.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@ActiveProfiles({"infrastructure-tests", "postgres"})
class PostgreSqlChatInfrastructureIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired UserRepository users;
    @Autowired ConversationService conversations;
    @Autowired MessageService messages;
    @Autowired MessageRepository messageRepository;
    @Autowired OutboxService outbox;
    @Autowired OutboxEventRepository outboxEvents;
    @Autowired OutboxPublisher publisher;
    @Autowired InMemoryQueueEventBus queue;

    @Test
    void concurrentWritersReceiveGaplessConversationSequence() throws Exception {
        AppUser alice = TestUsers.create(users, "psa_" + shortId());
        AppUser bob = TestUsers.create(users, "psb_" + shortId());
        UUID conversationId = conversations.createDirectConversation(alice.getId(),
                new CreateDirectConversationRequest(bob.getId())).id();

        runConcurrently(20, index -> messages.sendMessage(alice.getId(), conversationId,
                new SendMessageRequest(UUID.randomUUID(), "message-" + index, List.of())));

        List<Long> sequence = messageRepository.findByConversationIdAndSequenceNumberGreaterThanEqualOrderBySequenceNumberDesc(
                conversationId, 1, PageRequest.of(0, 30)).stream().map(Message::getSequenceNumber).sorted().toList();
        assertThat(sequence).containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(1, 20).boxed().toList());
    }

    @Test
    void canonicalDirectKeyPreventsTwoConversationsForOppositeRequests() throws Exception {
        AppUser alice = TestUsers.create(users, "pda_" + shortId());
        AppUser bob = TestUsers.create(users, "pdb_" + shortId());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<UUID> first = pool.submit(() -> conversations.createDirectConversation(alice.getId(),
                    new CreateDirectConversationRequest(bob.getId())).id());
            Future<UUID> second = pool.submit(() -> conversations.createDirectConversation(bob.getId(),
                    new CreateDirectConversationRequest(alice.getId())).id());
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void concurrentRetryWithSameCommandKeyCreatesOneMessage() throws Exception {
        AppUser alice = TestUsers.create(users, "pia_" + shortId());
        AppUser bob = TestUsers.create(users, "pib_" + shortId());
        UUID conversationId = conversations.createDirectConversation(alice.getId(),
                new CreateDirectConversationRequest(bob.getId())).id();
        UUID commandId = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<SendMessageResult> retry = () -> {
                start.await();
                return messages.sendMessageWithMetadata(alice.getId(), conversationId,
                        new SendMessageRequest(commandId, "same command", List.of()));
            };
            Future<SendMessageResult> first = pool.submit(retry);
            Future<SendMessageResult> second = pool.submit(retry);
            start.countDown();

            SendMessageResult a = first.get(10, TimeUnit.SECONDS);
            SendMessageResult b = second.get(10, TimeUnit.SECONDS);
            assertThat(a.message().id()).isEqualTo(b.message().id());
            assertThat(List.of(a.duplicate(), b.duplicate())).containsExactlyInAnyOrder(false, true);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void skipLockedClaimsEachOutboxRecordOnceAcrossWorkers() throws Exception {
        // Wiadomości utworzone w innych testach także zapisują outbox. Izolujemy ten
        // eksperyment, aby asercja dowodziła wyłącznie semantyki konkurencyjnego claimu.
        outboxEvents.deleteAll();
        int before = queue.size();
        for (int i = 0; i < 12; i++) outbox.append(UUID.randomUUID(), "claim.test", java.util.Map.of("i", i));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> one = pool.submit(publisher::publishPendingEvents);
            Future<?> two = pool.submit(publisher::publishPendingEvents);
            one.get(10, TimeUnit.SECONDS); two.get(10, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertThat(queue.size() - before).isEqualTo(12);
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void runConcurrently(int count, ThrowingConsumer action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) { int index = i; futures.add(pool.submit(() -> { start.await(); action.accept(index); return null; })); }
            start.countDown();
            for (Future<?> future : futures) future.get(20, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }

    @FunctionalInterface interface ThrowingConsumer { void accept(int index) throws Exception; }
}
