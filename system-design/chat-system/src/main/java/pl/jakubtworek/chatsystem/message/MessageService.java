package pl.jakubtworek.chatsystem.message;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.common.BadRequestException;
import pl.jakubtworek.chatsystem.common.NotFoundException;
import pl.jakubtworek.chatsystem.conversation.Conversation;
import pl.jakubtworek.chatsystem.conversation.ConversationMember;
import pl.jakubtworek.chatsystem.conversation.ConversationMemberRepository;
import pl.jakubtworek.chatsystem.conversation.ConversationRepository;
import pl.jakubtworek.chatsystem.conversation.ConversationService;
import pl.jakubtworek.chatsystem.media.Attachment;
import pl.jakubtworek.chatsystem.media.AttachmentService;
import pl.jakubtworek.chatsystem.moderation.ModerationService;
import pl.jakubtworek.chatsystem.messagestore.MessageStore;
import pl.jakubtworek.chatsystem.blocking.BlockingService;
import pl.jakubtworek.chatsystem.outbox.EventTypes;
import pl.jakubtworek.chatsystem.outbox.MessageCreatedEvent;
import pl.jakubtworek.chatsystem.outbox.OutboxService;
import pl.jakubtworek.chatsystem.user.AppUser;
import pl.jakubtworek.chatsystem.user.UserRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Główny serwis domenowy odpowiedzialny za obsługę wiadomości.
 *
 * To tutaj znajduje się właściwa logika aplikacji:
 * - wysyłanie wiadomości,
 * - deduplikacja po clientMessageId,
 * - walidacja treści i załączników,
 * - sprawdzanie członkostwa w konwersacji,
 * - blokady między użytkownikami,
 * - moderacja wiadomości,
 * - paginacja historii,
 * - statusy SENT / DELIVERED / READ,
 * - aktualizacja last message w konwersacji,
 * - publikacja zdarzenia do outboxa.
 *
 * Controller powinien tylko przyjąć request.
 * Ten serwis decyduje, czy operacja jest dozwolona i jak wpływa na stan systemu.
 */
@Service
public class MessageService {

    /**
     * Maksymalna liczba wiadomości zwracana w jednym requestcie.
     *
     * To chroni bazę przed zbyt ciężkimi zapytaniami,
     * np. gdy klient wyśle limit=100000.
     */
    private static final int MAX_LIMIT = 100;

    /**
     * Repozytorium JPA dla wiadomości.
     *
     * W tej wersji część odpowiedzialności została już przeniesiona do MessageStore,
     * dlatego messageRepository może być docelowo usunięte albo ograniczone.
     */
    private final MessageRepository messageRepository;

    /**
     * Abstrakcja nad miejscem przechowywania wiadomości.
     *
     * To ważny element Etapu 6:
     * aplikacja nie musi wiedzieć, czy wiadomości są w PostgreSQL,
     * ScyllaDB, Cassandrze, DynamoDB czy innym message store.
     */
    private final MessageStore messageStore;

    /**
     * Repozytorium statusów wiadomości per odbiorca.
     *
     * Status wiadomości nie jest tylko polem w Message,
     * bo w grupach każdy odbiorca może mieć inny stan:
     * SENT, DELIVERED albo READ.
     */
    private final MessageReceiptRepository receiptRepository;

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;

    /**
     * Serwis konwersacji używany głównie do sprawdzania uprawnień.
     *
     * Najważniejsze użycie: ensureMember().
     * Każda operacja na wiadomościach musi sprawdzić,
     * czy użytkownik należy do danej konwersacji.
     */
    private final ConversationService conversationService;

    private final UserRepository userRepository;

    /**
     * Outbox zapisuje zdarzenia biznesowe razem z transakcją.
     *
     * Dzięki temu nie ma sytuacji:
     * "wiadomość zapisana w bazie, ale event message.created nie został opublikowany".
     */
    private final OutboxService outboxService;

    /**
     * Serwis załączników.
     *
     * Przy wysyłaniu wiadomości weryfikuje,
     * czy podane attachmentIds należą do nadawcy i są gotowe do użycia.
     */
    private final AttachmentService attachmentService;

    /**
     * Serwis blokad użytkowników.
     *
     * Przed wysłaniem wiadomości sprawdza,
     * czy nadawca i odbiorcy nie blokują się wzajemnie.
     */
    private final BlockingService blockingService;

    /**
     * Serwis moderacji.
     *
     * Sprawdza treść wiadomości przed zapisem,
     * np. pod kątem zakazanych słów, spamu albo niedozwolonej treści.
     */
    private final ModerationService moderationService;

    public MessageService(
            MessageRepository messageRepository,
            MessageStore messageStore,
            MessageReceiptRepository receiptRepository,
            ConversationRepository conversationRepository,
            ConversationMemberRepository memberRepository,
            ConversationService conversationService,
            UserRepository userRepository,
            OutboxService outboxService,
            AttachmentService attachmentService,
            BlockingService blockingService,
            ModerationService moderationService
    ) {
        this.messageRepository = messageRepository;
        this.messageStore = messageStore;
        this.receiptRepository = receiptRepository;
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.conversationService = conversationService;
        this.userRepository = userRepository;
        this.outboxService = outboxService;
        this.attachmentService = attachmentService;
        this.blockingService = blockingService;
        this.moderationService = moderationService;
    }

    /**
     * Publiczna metoda wysyłania wiadomości używana przez REST API.
     *
     * Zwraca tylko MessageResponse, czyli format odpowiedzi dla klienta.
     * Szczegóły typu "czy to był duplikat" są ukryte.
     */
    @Transactional
    public MessageResponse sendMessage(UUID currentUserId, UUID conversationId, SendMessageRequest request) {
        return sendMessageWithMetadata(currentUserId, conversationId, request).message();
    }

    /**
     * Wysyła wiadomość i zwraca dodatkową informację,
     * czy request był duplikatem.
     *
     * To jest kluczowe przy WebSocket/reconnect/retry:
     * klient może wysłać tę samą wiadomość ponownie,
     * ale backend nie powinien utworzyć drugiego rekordu.
     */
    @Transactional
    public SendMessageResult sendMessageWithMetadata(UUID currentUserId, UUID conversationId, SendMessageRequest request) {

        /*
         * Najpierw sprawdzamy, czy użytkownik ma prawo pisać w tej konwersacji.
         * Bez tego ktoś mógłby próbować wysłać wiadomość do cudzej rozmowy,
         * znając tylko conversationId.
         */
        conversationService.ensureMember(conversationId, currentUserId);

        /*
         * Wiadomość musi mieć tekst albo przynajmniej jeden załącznik.
         */
        validateMessagePayload(request);

        /*
         * Moderacja działa przed zapisem.
         * Jeśli wiadomość łamie reguły, nie trafia do bazy.
         */
        moderationService.validateOutgoingMessage(request.body());

        /*
         * Sprawdzenie blokad.
         * Jeżeli nadawca blokuje odbiorcę albo odbiorca blokuje nadawcę,
         * wiadomość nie powinna zostać wysłana.
         */
        ensureMessageAllowed(currentUserId, conversationId);

        /*
         * Pierwsza warstwa deduplikacji.
         *
         * clientMessageId jest generowane po stronie klienta.
         * Jeśli klient ponowi request po timeoutcie albo reconnect,
         * zwracamy istniejącą wiadomość zamiast tworzyć nową.
         */
        var existing = messageStore.findBySenderAndClientMessageId(currentUserId, request.clientMessageId());
        if (existing.isPresent()) {
            return new SendMessageResult(toResponseForViewer(existing.get(), currentUserId), true);
        }

        try {
            /*
             * Normalna ścieżka — wiadomość jeszcze nie istnieje,
             * więc tworzymy ją w bazie.
             */
            return new SendMessageResult(createMessage(currentUserId, conversationId, request), false);
        } catch (DataIntegrityViolationException ex) {
            /*
             * Druga warstwa deduplikacji.
             *
             * Chroni przed race condition:
             * dwa identyczne requesty mogą wejść równolegle.
             * Jeden zapis się uda, drugi uderzy w unique constraint.
             *
             * Zamiast zwracać błąd, odczytujemy już zapisaną wiadomość
             * i traktujemy request jako duplikat.
             */
            Message duplicate = messageStore.findBySenderAndClientMessageId(currentUserId, request.clientMessageId())
                    .orElseThrow(() -> ex);
            return new SendMessageResult(toResponseForViewer(duplicate, currentUserId), true);
        }
    }

    /**
     * Pobiera historię wiadomości w konwersacji.
     *
     * Obsługuje paginację wsteczną:
     * - bez before zwraca najnowsze wiadomości,
     * - z before zwraca wiadomości starsze niż wskazany timestamp.
     */
    @Transactional(readOnly = true)
    public MessagePageResponse getMessages(UUID currentUserId, UUID conversationId, Instant before, int limit) {
        conversationService.ensureMember(conversationId, currentUserId);

        /*
         * Limit jest normalizowany, żeby nie dopuścić do przeciążenia bazy.
         * Pobieramy pageSize + 1, żeby sprawdzić, czy istnieje kolejna strona.
         */
        int pageSize = normalizeLimit(limit);
        PageRequest page = PageRequest.of(0, pageSize + 1);

        List<Message> fetched = before == null
                ? messageStore.latestForConversation(conversationId, page)
                : messageStore.before(conversationId, before, page);

        boolean hasMore = fetched.size() > pageSize;

        /*
         * Do odpowiedzi zwracamy tylko właściwy rozmiar strony.
         * Dodatkowy rekord służy wyłącznie do ustalenia hasMore.
         */
        List<Message> pageItems = fetched.stream().limit(pageSize).toList();

        /*
         * Store może zwracać najnowsze wiadomości malejąco.
         * Dla UI czatu wygodniej jest zwrócić je rosnąco po czasie.
         */
        List<MessageResponse> ordered = toResponsesForViewer(pageItems, currentUserId).stream()
                .sorted(Comparator.comparing(MessageResponse::createdAt))
                .toList();

        /*
         * nextBefore to cursor dla kolejnego requestu historii.
         * Frontend używa go do infinite scroll.
         */
        Instant nextBefore = hasMore && !pageItems.isEmpty()
                ? pageItems.get(pageItems.size() - 1).getCreatedAt()
                : null;

        return new MessagePageResponse(ordered, nextBefore, hasMore);
    }

    /**
     * Pobiera wiadomości utworzone po wskazanym czasie.
     *
     * To endpoint wspierający reconnect/offline sync.
     * Jeśli klient straci WebSocket, po powrocie może pobrać brakujące wiadomości.
     */
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessagesSince(UUID currentUserId, UUID conversationId, Instant after, int limit) {
        conversationService.ensureMember(conversationId, currentUserId);

        int pageSize = normalizeLimit(limit);
        PageRequest page = PageRequest.of(0, pageSize);

        List<Message> messages = messageStore.after(conversationId, after, page);
        return toResponsesForViewer(messages, currentUserId);
    }

    /**
     * Oznacza jedną konkretną wiadomość jako dostarczoną.
     *
     * DELIVERED oznacza, że aplikacja klienta odebrała wiadomość.
     * Nie oznacza jeszcze, że użytkownik ją przeczytał.
     */
    @Transactional
    public ReceiptResponse markDelivered(UUID currentUserId, UUID conversationId, UUID messageId) {
        conversationService.ensureMember(conversationId, currentUserId);
        getMessageInConversation(conversationId, messageId);

        MessageReceipt receipt = getReceiptForRecipient(messageId, currentUserId);
        Instant now = Instant.now();

        receipt.markDelivered(now);
        receiptRepository.save(receipt);

        return toReceiptResponse(conversationId, messageId, currentUserId, receipt);
    }

    /**
     * Oznacza jedną konkretną wiadomość jako przeczytaną.
     *
     * READ jest silniejszym stanem niż DELIVERED.
     * W praktyce przeczytanie wiadomości powinno też oznaczać jej dostarczenie.
     */
    @Transactional
    public ReceiptResponse markRead(UUID currentUserId, UUID conversationId, UUID messageId) {
        conversationService.ensureMember(conversationId, currentUserId);

        Message message = getMessageInConversation(conversationId, messageId);
        MessageReceipt receipt = getReceiptForRecipient(messageId, currentUserId);
        Instant now = Instant.now();

        receipt.markRead(now);
        receiptRepository.save(receipt);

        /*
         * Aktualizujemy stan członka konwersacji.
         * lastReadAt jest później używane m.in. do liczenia unread count.
         */
        ConversationMember member = memberRepository.findByConversationIdAndUserId(conversationId, currentUserId)
                .orElseThrow(() -> new NotFoundException("Conversation member not found"));

        member.markRead(message.getCreatedAt());
        memberRepository.save(member);

        /*
         * Jeśli użytkownik przeczytał wiadomość X,
         * wcześniejsze wiadomości w tej konwersacji też traktujemy jako przeczytane.
         */
        markEarlierMessagesRead(conversationId, currentUserId, messageId, now);

        return toReceiptResponse(conversationId, messageId, currentUserId, receipt);
    }

    /**
     * Oznacza jako dostarczone wszystkie wiadomości do wskazanej wiadomości włącznie.
     *
     * To bardziej praktyczne niż oznaczanie każdej wiadomości osobno,
     * bo klient może wysłać jeden request po pobraniu paczki wiadomości.
     */
    @Transactional
    public ReceiptResponse markDeliveredUpTo(UUID currentUserId, UUID conversationId, UUID messageId) {
        conversationService.ensureMember(conversationId, currentUserId);
        getMessageInConversation(conversationId, messageId);

        Instant now = Instant.now();

        List<MessageReceipt> receipts =
                receiptRepository.findUndeliveredReceiptsUpTo(conversationId, currentUserId, messageId);

        receipts.forEach(receipt -> receipt.markDelivered(now));
        receiptRepository.saveAll(receipts);

        MessageReceipt receipt = getReceiptForRecipient(messageId, currentUserId);
        return toReceiptResponse(conversationId, messageId, currentUserId, receipt);
    }

    /**
     * Oznacza jako przeczytane wszystkie wiadomości do wskazanej wiadomości włącznie.
     *
     * To główny mechanizm dla unread count:
     * gdy użytkownik wejdzie do rozmowy i zobaczy ostatnią wiadomość,
     * backend może wyzerować nieprzeczytane wiadomości do tego punktu.
     */
    @Transactional
    public ReceiptResponse markReadUpTo(UUID currentUserId, UUID conversationId, UUID messageId) {
        conversationService.ensureMember(conversationId, currentUserId);

        Message boundary = getMessageInConversation(conversationId, messageId);
        Instant now = Instant.now();

        List<MessageReceipt> receipts =
                receiptRepository.findUnreadReceiptsUpTo(conversationId, currentUserId, messageId);

        receipts.forEach(receipt -> receipt.markRead(now));
        receiptRepository.saveAll(receipts);

        ConversationMember member = memberRepository.findByConversationIdAndUserId(conversationId, currentUserId)
                .orElseThrow(() -> new NotFoundException("Conversation member not found"));

        member.markRead(boundary.getCreatedAt());
        memberRepository.save(member);

        MessageReceipt receipt = getReceiptForRecipient(messageId, currentUserId);
        return toReceiptResponse(conversationId, messageId, currentUserId, receipt);
    }

    /**
     * Normalizuje limit wyników.
     *
     * Minimalnie zwracamy 1 element.
     * Maksymalnie MAX_LIMIT.
     */
    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    /**
     * Tworzy i zapisuje nową wiadomość.
     *
     * To najważniejsza metoda zapisu:
     * - ładuje konwersację,
     * - ładuje nadawcę,
     * - przygotowuje treść,
     * - sprawdza załączniki,
     * - zapisuje wiadomość,
     * - tworzy receipts dla odbiorców,
     * - aktualizuje lastMessage konwersacji,
     * - zapisuje event do outboxa.
     */
    private MessageResponse createMessage(UUID currentUserId, UUID conversationId, SendMessageRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));

        AppUser sender = userRepository.findById(currentUserId)
                .orElseThrow(() -> new NotFoundException("Current user not found"));

        /*
         * Normalizacja treści.
         * Puste stringi nie powinny być traktowane jako realna treść wiadomości.
         */
        String body = request.body() == null ? null : request.body().trim();

        /*
         * Załączniki muszą:
         * - istnieć,
         * - należeć do nadawcy,
         * - być gotowe do użycia.
         */
        List<Attachment> attachments =
                attachmentService.getReadyAttachmentsOwnedBy(currentUserId, request.attachmentIds());

        Message message = new Message(
                conversation,
                sender,
                request.clientMessageId(),
                body == null || body.isBlank() ? null : body,
                attachments
        );

        Message saved = messageStore.save(message);

        /*
         * Tworzymy MessageReceipt dla wszystkich odbiorców poza nadawcą.
         *
         * Dzięki temu każdy odbiorca może mieć własny status:
         * SENT, DELIVERED albo READ.
         */
        List<ConversationMember> members = memberRepository.findMembersByConversationId(conversationId);

        List<MessageReceipt> receipts = members.stream()
                .filter(member -> !member.getUser().getId().equals(currentUserId))
                .map(member -> new MessageReceipt(saved, conversation, member.getUser()))
                .toList();

        receiptRepository.saveAll(receipts);

        /*
         * Aktualizacja konwersacji potrzebna do listy rozmów:
         * frontend może pokazać ostatnią wiadomość bez dociągania całej historii.
         */
        conversation.updateLastMessage(saved);
        conversationRepository.save(conversation);

        /*
         * Outbox event.
         *
         * Inne części systemu mogą później asynchronicznie zareagować:
         * - WebSocket delivery,
         * - push notifications,
         * - search indexing,
         * - analytics.
         */
        outboxService.append(
                saved.getId(),
                EventTypes.MESSAGE_CREATED,
                new MessageCreatedEvent(saved.getId(), conversationId, currentUserId, saved.getCreatedAt())
        );

        /*
         * Dla nadawcy świeżo zapisana wiadomość ma status SENT.
         */
        return MessageResponse.from(saved, MessageStatus.SENT, null, null);
    }

    /**
     * Adapter dla wyszukiwania.
     *
     * Search service może pobrać listę Message,
     * a tutaj zamienia je na MessageResponse z punktu widzenia konkretnego użytkownika.
     */
    public List<MessageResponse> toResponsesForSearch(UUID viewerId, List<Message> messages) {
        return toResponsesForViewer(messages, viewerId);
    }

    /**
     * Sprawdza, czy wiadomość ma jakąkolwiek treść.
     *
     * Dozwolone są:
     * - wiadomość tekstowa,
     * - wiadomość z załącznikiem,
     * - wiadomość z tekstem i załącznikiem.
     *
     * Niedozwolona jest całkowicie pusta wiadomość.
     */
    private void validateMessagePayload(SendMessageRequest request) {
        boolean hasBody = request.body() != null && !request.body().isBlank();
        boolean hasAttachments = request.attachmentIds() != null && !request.attachmentIds().isEmpty();

        if (!hasBody && !hasAttachments) {
            throw new BadRequestException("Message must contain text or at least one attachment");
        }
    }

    /**
     * Sprawdza, czy nadawca może wysłać wiadomość do wszystkich członków konwersacji.
     *
     * Dla rozmowy 1:1 sprawa jest prosta.
     * Dla grupy trzeba sprawdzić relację z każdym członkiem.
     *
     * Jeśli istnieje blokada w dowolną stronę,
     * wysyłka jest blokowana.
     */
    private void ensureMessageAllowed(UUID senderId, UUID conversationId) {
        List<ConversationMember> members = memberRepository.findMembersByConversationId(conversationId);

        for (ConversationMember member : members) {
            UUID recipientId = member.getUser().getId();

            if (!recipientId.equals(senderId)) {
                blockingService.ensureNotBlockedEitherWay(senderId, recipientId);
            }
        }
    }

    /**
     * Pobiera wiadomość i sprawdza, czy faktycznie należy do wskazanej konwersacji.
     *
     * To zabezpiecza przed sytuacją,
     * w której użytkownik poda messageId z innej rozmowy.
     */
    private Message getMessageInConversation(UUID conversationId, UUID messageId) {
        Message message = messageStore.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found"));

        if (!message.getConversation().getId().equals(conversationId)) {
            throw new NotFoundException("Message not found in this conversation");
        }

        return message;
    }

    /**
     * Pobiera receipt dla konkretnej wiadomości i odbiorcy.
     *
     * Receipt istnieje tylko dla odbiorców.
     * Nadawca nie ma własnego receipt dla swojej wiadomości.
     */
    private MessageReceipt getReceiptForRecipient(UUID messageId, UUID recipientId) {
        return receiptRepository.findByMessageIdAndRecipientId(messageId, recipientId)
                .orElseThrow(() -> new NotFoundException("Receipt not found for this user and message"));
    }

    /**
     * Oznacza wcześniejsze wiadomości jako przeczytane.
     *
     * W aplikacji czatu działa to naturalnie:
     * jeśli użytkownik przeczytał wiadomość nr 50,
     * to wiadomości 1-49 również uznajemy za przeczytane.
     */
    private void markEarlierMessagesRead(UUID conversationId, UUID currentUserId, UUID messageId, Instant now) {
        List<MessageReceipt> receipts =
                receiptRepository.findUnreadReceiptsUpTo(conversationId, currentUserId, messageId);

        receipts.forEach(receipt -> receipt.markRead(now));
        receiptRepository.saveAll(receipts);
    }

    /**
     * Zamienia listę encji Message na odpowiedzi API z perspektywy konkretnego użytkownika.
     *
     * To ważne, bo status wiadomości zależy od tego, kto patrzy:
     * - odbiorca widzi swój receipt,
     * - nadawca widzi zagregowany status odbiorców.
     */
    private List<MessageResponse> toResponsesForViewer(List<Message> messages, UUID viewerId) {
        if (messages.isEmpty()) {
            return List.of();
        }

        /*
         * Pobieramy receipts hurtowo dla wszystkich wiadomości,
         * żeby uniknąć problemu N+1 queries.
         */
        List<UUID> messageIds = messages.stream()
                .map(Message::getId)
                .toList();

        List<MessageReceipt> receipts = receiptRepository.findAllForMessages(messageIds);

        Map<UUID, List<MessageReceipt>> receiptsByMessage = receipts.stream()
                .collect(Collectors.groupingBy(receipt -> receipt.getMessage().getId()));

        return messages.stream()
                .map(message -> toResponseForViewer(
                        message,
                        viewerId,
                        receiptsByMessage.getOrDefault(message.getId(), List.of())
                ))
                .toList();
    }

    /**
     * Zamienia pojedynczą wiadomość na MessageResponse.
     *
     * Ta wersja sama dociąga receipts z bazy.
     * Jest wygodna dla pojedynczych przypadków, ale dla listy wiadomości
     * lepsza jest metoda batchowa powyżej.
     */
    private MessageResponse toResponseForViewer(Message message, UUID viewerId) {
        List<MessageReceipt> receipts = receiptRepository.findAllForMessages(List.of(message.getId()));
        return toResponseForViewer(message, viewerId, receipts);
    }

    /**
     * Buduje odpowiedź wiadomości z właściwym statusem dla konkretnego użytkownika.
     *
     * Najważniejsza część:
     * status wiadomości nie jest globalny.
     * Zależy od roli użytkownika względem wiadomości.
     */
    private MessageResponse toResponseForViewer(Message message, UUID viewerId, List<MessageReceipt> receipts) {

        /*
         * Jeśli viewer nie jest nadawcą, pokazujemy jego własny receipt.
         *
         * Przykład:
         * użytkownik B patrzy na wiadomość od użytkownika A.
         * Status mówi, czy B ją dostał/przeczytał.
         */
        if (!message.getSender().getId().equals(viewerId)) {
            return receipts.stream()
                    .filter(receipt -> receipt.getRecipient().getId().equals(viewerId))
                    .findFirst()
                    .map(receipt -> MessageResponse.from(
                            message,
                            receipt.status(),
                            receipt.getDeliveredAt(),
                            receipt.getReadAt()
                    ))
                    .orElseGet(() -> MessageResponse.from(message, MessageStatus.SENT, null, null));
        }

        /*
         * Jeśli viewer jest nadawcą, status jest agregowany po odbiorcach.
         *
         * W 1:1 to praktycznie status drugiego użytkownika.
         * W grupie:
         * - READ dopiero gdy wszyscy przeczytali,
         * - DELIVERED dopiero gdy wszyscy odebrali,
         * - w przeciwnym razie SENT.
         */
        if (receipts.isEmpty()) {
            return MessageResponse.from(message, MessageStatus.SENT, null, null);
        }

        boolean allRead = receipts.stream().allMatch(receipt -> receipt.getReadAt() != null);
        boolean allDelivered = receipts.stream().allMatch(receipt -> receipt.getDeliveredAt() != null);

        /*
         * Dla uproszczonego API zwracamy najpóźniejszy deliveredAt/readAt.
         * Przy dużych grupach docelowo można rozważyć bardziej szczegółowy model.
         */
        Instant deliveredAt = receipts.stream()
                .map(MessageReceipt::getDeliveredAt)
                .filter(value -> value != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        Instant readAt = receipts.stream()
                .map(MessageReceipt::getReadAt)
                .filter(value -> value != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        MessageStatus status =
                allRead ? MessageStatus.READ :
                        allDelivered ? MessageStatus.DELIVERED :
                                MessageStatus.SENT;

        return MessageResponse.from(message, status, deliveredAt, readAt);
    }

    /**
     * Tworzy odpowiedź API dla operacji delivered/read.
     *
     * Klient po tej odpowiedzi wie,
     * jaki status został zapisany i z jakimi timestampami.
     */
    private ReceiptResponse toReceiptResponse(
            UUID conversationId,
            UUID messageId,
            UUID currentUserId,
            MessageReceipt receipt
    ) {
        return new ReceiptResponse(
                conversationId,
                messageId,
                currentUserId,
                receipt.status(),
                receipt.getDeliveredAt(),
                receipt.getReadAt()
        );
    }
}