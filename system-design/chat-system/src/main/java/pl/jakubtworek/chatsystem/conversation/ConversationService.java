package pl.jakubtworek.chatsystem.conversation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.blocking.BlockingService;
import pl.jakubtworek.chatsystem.common.BadRequestException;
import pl.jakubtworek.chatsystem.common.ForbiddenException;
import pl.jakubtworek.chatsystem.common.NotFoundException;
import pl.jakubtworek.chatsystem.message.MessageReceiptRepository;
import pl.jakubtworek.chatsystem.message.MessageResponse;
import pl.jakubtworek.chatsystem.message.MessageStatus;
import pl.jakubtworek.chatsystem.user.AppUser;
import pl.jakubtworek.chatsystem.user.UserRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Serwis odpowiedzialny za logikę konwersacji.
 *
 * Obsługuje:
 * - tworzenie rozmów 1:1,
 * - tworzenie grup,
 * - listę konwersacji użytkownika,
 * - pobieranie szczegółów konwersacji,
 * - dodawanie członków do grup,
 * - zmianę ról członków,
 * - usuwanie członków,
 * - sprawdzanie członkostwa użytkownika w rozmowie.
 *
 * To jest warstwa domenowa.
 * Controller tylko przekazuje request, a ten serwis decyduje,
 * czy operacja jest dozwolona i jak zmienia stan aplikacji.
 */
@Service
public class ConversationService {

    /**
     * Repozytorium konwersacji.
     *
     * Służy do zapisu i odczytu encji Conversation:
     * DIRECT albo GROUP.
     */
    private final ConversationRepository conversationRepository;

    /**
     * Repozytorium członków konwersacji.
     *
     * To bardzo ważna zależność, bo członkostwo decyduje:
     * - kto może czytać rozmowę,
     * - kto może pisać,
     * - kto ma jaką rolę,
     * - komu liczyć unread count,
     * - komu wysyłać eventy realtime.
     */
    private final ConversationMemberRepository memberRepository;

    /**
     * Repozytorium receiptów wiadomości.
     *
     * Używane tutaj głównie do policzenia unreadCount
     * przy budowaniu odpowiedzi dla listy konwersacji.
     */
    private final MessageReceiptRepository receiptRepository;

    /**
     * Repozytorium użytkowników.
     *
     * Potrzebne przy tworzeniu rozmów i dodawaniu członków,
     * żeby sprawdzić, czy wskazany użytkownik istnieje.
     */
    private final UserRepository userRepository;

    /**
     * Serwis blokad użytkowników.
     *
     * Przy rozmowie 1:1 zabezpiecza przed utworzeniem rozmowy,
     * jeśli użytkownicy blokują się wzajemnie.
     *
     * Uwaga: w tej wersji jest używany przy direct conversation,
     * ale przy tworzeniu/dodawaniu do grupy też warto rozważyć blokady.
     */
    private final BlockingService blockingService;

    public ConversationService(
            ConversationRepository conversationRepository,
            ConversationMemberRepository memberRepository,
            MessageReceiptRepository receiptRepository,
            UserRepository userRepository,
            BlockingService blockingService
    ) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.receiptRepository = receiptRepository;
        this.userRepository = userRepository;
        this.blockingService = blockingService;
    }

    /**
     * Tworzy rozmowę 1:1 albo zwraca istniejącą.
     *
     * Najważniejsze zasady:
     * - nie można utworzyć rozmowy z samym sobą,
     * - obaj użytkownicy muszą istnieć,
     * - użytkownicy nie mogą blokować się wzajemnie,
     * - nie tworzymy duplikatu rozmowy 1:1, jeśli już istnieje.
     */
    @Transactional
    public ConversationResponse createDirectConversation(UUID currentUserId, CreateDirectConversationRequest request) {

        /*
         * Rozmowa 1:1 z samym sobą nie ma sensu produktowo
         * i mogłaby psuć założenia modelu direct conversation.
         */
        if (currentUserId.equals(request.participantId())) {
            throw new BadRequestException("Cannot create direct conversation with yourself");
        }

        /*
         * Pobieramy aktualnego użytkownika i drugiego uczestnika.
         * Jeśli którykolwiek nie istnieje, przerywamy operację.
         */
        AppUser currentUser = getUser(currentUserId, "Current user not found");
        AppUser participant = getUser(request.participantId(), "Participant not found");

        /*
         * Blokady są sprawdzane przed utworzeniem rozmowy.
         *
         * Jeśli A blokuje B albo B blokuje A,
         * nie pozwalamy utworzyć rozmowy 1:1.
         */
        blockingService.ensureNotBlockedEitherWay(currentUserId, participant.getId());

        /*
         * Najpierw szukamy istniejącej rozmowy direct między tymi użytkownikami.
         *
         * To zabezpiecza przed duplikatami typu:
         * - User A tworzy rozmowę z User B,
         * - User B tworzy rozmowę z User A,
         * - backend tworzy dwie różne konwersacje.
         */
        return memberRepository.findDirectConversationBetween(currentUserId, participant.getId())
                .map(existing -> toResponse(
                        existing,
                        memberRepository.findMembersByConversationId(existing.getId()),
                        currentUserId
                ))
                .orElseGet(() -> {
                    /*
                     * Jeśli rozmowa nie istnieje, tworzymy nową Conversation typu DIRECT.
                     *
                     * Obaj użytkownicy dostają rolę OWNER.
                     * W direct conversation role nie mają dużego znaczenia,
                     * ale upraszcza to model, bo każdy członek zawsze ma jakąś rolę.
                     */
                    Conversation conversation = new Conversation(ConversationType.DIRECT);

                    ConversationMember memberA =
                            new ConversationMember(conversation, currentUser, ConversationRole.OWNER);

                    ConversationMember memberB =
                            new ConversationMember(conversation, participant, ConversationRole.OWNER);

                    conversation.addMember(memberA);
                    conversation.addMember(memberB);

                    Conversation saved = conversationRepository.save(conversation);

                    return toResponse(saved, saved.getMembers(), currentUserId);
                });
    }

    /**
     * Tworzy nową konwersację grupową.
     *
     * Aktualny użytkownik zostaje OWNER-em.
     * Pozostali uczestnicy z requestu są dodawani jako MEMBER.
     */
    @Transactional
    public ConversationResponse createGroupConversation(UUID currentUserId, CreateGroupConversationRequest request) {

        /*
         * Twórca grupy musi istnieć.
         */
        AppUser owner = getUser(currentUserId, "Current user not found");

        /*
         * Tworzymy Conversation typu GROUP z tytułem i właścicielem.
         *
         * request.title().trim() zakłada, że walidacja DTO nie przepuści nulla
         * ani pustego tytułu.
         */
        Conversation conversation =
                new Conversation(ConversationType.GROUP, request.title().trim(), owner);

        /*
         * Twórca grupy dostaje rolę OWNER.
         *
         * OWNER ma najwyższe uprawnienia, np. zarządzanie rolami.
         */
        conversation.addMember(
                new ConversationMember(conversation, owner, ConversationRole.OWNER)
        );

        /*
         * HashSet usuwa duplikaty participantIds.
         *
         * Usuwamy też currentUserId, bo twórca został już dodany jako OWNER.
         */
        Set<UUID> participants = new HashSet<>(request.participantIds());
        participants.remove(currentUserId);

        /*
         * Każdy dodatkowy uczestnik musi istnieć.
         * W tej wersji dostaje rolę MEMBER.
         */
        for (UUID participantId : participants) {
            AppUser participant = getUser(
                    participantId,
                    "Participant not found: " + participantId
            );

            conversation.addMember(
                    new ConversationMember(conversation, participant, ConversationRole.MEMBER)
            );
        }

        Conversation saved = conversationRepository.save(conversation);
        return toResponse(saved, saved.getMembers(), currentUserId);
    }

    /**
     * Pobiera listę konwersacji aktualnego użytkownika.
     *
     * To endpoint pod ekran inbox/listę rozmów.
     *
     * Zwracane są tylko te konwersacje,
     * w których currentUserId jest członkiem.
     */
    @Transactional(readOnly = true)
    public List<ConversationResponse> getMyConversations(UUID currentUserId) {
        return memberRepository.findConversationsForUser(currentUserId)
                .stream()
                .map(conversation -> toResponse(
                        conversation,
                        memberRepository.findMembersByConversationId(conversation.getId()),
                        currentUserId
                ))
                .toList();
    }

    /**
     * Pobiera szczegóły jednej konwersacji.
     *
     * Przed zwróceniem danych sprawdzamy membership.
     * Bez tego użytkownik mógłby próbować odczytać cudzą rozmowę po conversationId.
     */
    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UUID currentUserId, UUID conversationId) {

        /*
         * Kluczowe zabezpieczenie dostępu.
         */
        ensureMember(conversationId, currentUserId);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));

        return toResponse(
                conversation,
                memberRepository.findMembersByConversationId(conversationId),
                currentUserId
        );
    }

    /**
     * Dodaje użytkownika do grupy.
     *
     * Operacja dotyczy tylko konwersacji GROUP.
     * Użytkownik wykonujący operację musi mieć uprawnienia do zarządzania członkami.
     */
    @Transactional
    public ConversationResponse addMember(
            UUID currentUserId,
            UUID conversationId,
            AddGroupMemberRequest request
    ) {
        /*
         * Pobiera rozmowę i sprawdza, czy jest grupą.
         * Nie można dodawać członków do rozmowy DIRECT.
         */
        Conversation conversation = getGroupConversation(conversationId);

        /*
         * Tylko odpowiednie role, np. OWNER/ADMIN,
         * mogą dodawać członków.
         */
        ensureCanManageMembers(conversationId, currentUserId);

        /*
         * Nie dodajemy drugi raz tego samego użytkownika.
         */
        if (memberRepository.existsByConversationIdAndUserId(conversationId, request.userId())) {
            throw new BadRequestException("User is already a member of this conversation");
        }

        AppUser user = getUser(request.userId(), "User not found");

        /*
         * Jeśli rola nie została podana, domyślnie dodajemy jako MEMBER.
         */
        ConversationRole role = request.role() == null
                ? ConversationRole.MEMBER
                : request.role();

        /*
         * Nadanie roli OWNER jest mocniejszą operacją niż zwykłe dodanie członka.
         * Dlatego wymagamy uprawnień do zarządzania rolami.
         */
        if (role == ConversationRole.OWNER) {
            ensureCanManageRoles(conversationId, currentUserId);
        }

        ConversationMember member = new ConversationMember(conversation, user, role);
        conversation.addMember(member);

        conversationRepository.save(conversation);

        return toResponse(
                conversation,
                memberRepository.findMembersByConversationId(conversationId),
                currentUserId
        );
    }

    /**
     * Zmienia rolę członka grupy.
     *
     * Tylko użytkownik z uprawnieniami do zarządzania rolami
     * może wykonać tę operację.
     */
    @Transactional
    public ConversationResponse updateMemberRole(
            UUID currentUserId,
            UUID conversationId,
            UUID targetUserId,
            UpdateMemberRoleRequest request
    ) {
        Conversation conversation = getGroupConversation(conversationId);

        /*
         * W tej implementacji tylko OWNER może zarządzać rolami.
         */
        ensureCanManageRoles(conversationId, currentUserId);

        ConversationMember target = memberRepository
                .findByConversationIdAndUserId(conversationId, targetUserId)
                .orElseThrow(() -> new NotFoundException("Member not found"));

        /*
         * Owner nie może sam siebie zdegradować.
         *
         * To chroni grupę przed sytuacją,
         * w której nie zostaje żaden użytkownik z pełnymi uprawnieniami.
         */
        if (target.getUser().getId().equals(currentUserId)
                && target.getRole() == ConversationRole.OWNER
                && request.role() != ConversationRole.OWNER) {
            throw new BadRequestException("Owner cannot demote themselves");
        }

        target.changeRole(request.role());
        memberRepository.save(target);

        return toResponse(
                conversation,
                memberRepository.findMembersByConversationId(conversationId),
                currentUserId
        );
    }

    /**
     * Usuwa członka z grupy albo pozwala użytkownikowi opuścić grupę.
     *
     * Zasady:
     * - użytkownik może usuwać innych tylko, jeśli ma odpowiednią rolę,
     * - owner nie może być usunięty przez kogoś innego,
     * - owner nie może opuścić grupy, jeśli są inni członkowie,
     *   dopóki nie przekaże ownershipu.
     */
    @Transactional
    public ConversationResponse removeMember(UUID currentUserId, UUID conversationId, UUID targetUserId) {
        Conversation conversation = getGroupConversation(conversationId);

        /*
         * current = użytkownik wykonujący operację.
         * target = użytkownik usuwany z grupy.
         */
        ConversationMember current = getMember(conversationId, currentUserId);
        ConversationMember target = getMember(conversationId, targetUserId);

        boolean removingSelf = currentUserId.equals(targetUserId);

        /*
         * Jeśli użytkownik usuwa kogoś innego,
         * musi mieć uprawnienia do zarządzania członkami.
         */
        if (!removingSelf && !current.getRole().canManageMembers()) {
            throw new ForbiddenException("You cannot remove members from this group");
        }

        /*
         * Owner może opuścić grupę sam,
         * ale nie może być usunięty przez innego użytkownika.
         */
        if (target.getRole() == ConversationRole.OWNER && !removingSelf) {
            throw new ForbiddenException("Owner can only leave by themselves");
        }

        /*
         * Jeśli owner próbuje opuścić grupę, a grupa ma jeszcze innych członków,
         * wymagamy wcześniejszego przekazania ownershipu.
         */
        if (target.getRole() == ConversationRole.OWNER
                && memberRepository.countByConversationId(conversationId) > 1) {
            throw new BadRequestException("Transfer ownership before leaving the group");
        }

        /*
         * Budujemy odpowiedź przed usunięciem.
         *
         * Jest to potrzebne szczególnie przy removingSelf,
         * bo po usunięciu użytkownik nie będzie już członkiem rozmowy
         * i toResponse rzuciłoby ForbiddenException.
         */
        ConversationResponse responseBeforeDelete = toResponse(
                conversation,
                memberRepository.findMembersByConversationId(conversationId),
                currentUserId
        );

        memberRepository.delete(target);

        /*
         * Jeśli użytkownik usuwa sam siebie, zwracamy stan sprzed usunięcia.
         * Frontend i tak powinien potem usunąć rozmowę z listy użytkownika.
         */
        if (removingSelf) {
            return responseBeforeDelete;
        }

        return toResponse(
                conversation,
                memberRepository.findMembersByConversationId(conversationId),
                currentUserId
        );
    }

    /**
     * Sprawdza, czy użytkownik jest członkiem konwersacji.
     *
     * To jedna z najważniejszych metod bezpieczeństwa w całym systemie.
     *
     * Używana przez:
     * - MessageService,
     * - RealtimeMessageController,
     * - WebSocketAuthorizationInterceptor,
     * - endpointy pobierające konwersacje.
     *
     * Jeśli użytkownik nie jest członkiem,
     * nie powinien móc czytać, pisać ani subskrybować eventów tej rozmowy.
     */
    public void ensureMember(UUID conversationId, UUID userId) {
        if (!memberRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new ForbiddenException("You are not a member of this conversation");
        }
    }

    /**
     * Sprawdza, czy użytkownik może zarządzać członkami grupy.
     *
     * Typowo uprawnienie mają OWNER i ADMIN.
     */
    private void ensureCanManageMembers(UUID conversationId, UUID userId) {
        ConversationMember member = getMember(conversationId, userId);

        if (!member.getRole().canManageMembers()) {
            throw new ForbiddenException("You cannot manage members in this conversation");
        }
    }

    /**
     * Sprawdza, czy użytkownik może zarządzać rolami.
     *
     * Typowo to uprawnienie powinien mieć tylko OWNER.
     */
    private void ensureCanManageRoles(UUID conversationId, UUID userId) {
        ConversationMember member = getMember(conversationId, userId);

        if (!member.getRole().canManageRoles()) {
            throw new ForbiddenException("Only owner can manage roles");
        }
    }

    /**
     * Pobiera członkostwo użytkownika w konwersacji.
     *
     * Jeśli nie ma członkostwa, traktujemy to jako brak dostępu.
     */
    private ConversationMember getMember(UUID conversationId, UUID userId) {
        return memberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this conversation"));
    }

    /**
     * Pobiera konwersację i sprawdza, że jest typu GROUP.
     *
     * Używane przy operacjach administracyjnych na grupie:
     * - dodawanie członków,
     * - zmiana ról,
     * - usuwanie członków.
     */
    private Conversation getGroupConversation(UUID conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));

        if (conversation.getType() != ConversationType.GROUP) {
            throw new BadRequestException("This operation is available only for group conversations");
        }

        return conversation;
    }

    /**
     * Pobiera użytkownika po id albo rzuca NotFoundException.
     *
     * Centralizuje powtarzalny kod odczytu użytkownika.
     */
    private AppUser getUser(UUID userId, String message) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(message));
    }

    /**
     * Buduje ConversationResponse dla aktualnego użytkownika.
     *
     * To nie jest zwykłe mapowanie encji na DTO.
     * Odpowiedź zależy od currentUserId, bo zawiera:
     * - rolę aktualnego użytkownika,
     * - unreadCount dla aktualnego użytkownika,
     * - lastReadAt aktualnego użytkownika.
     */
    private ConversationResponse toResponse(
            Conversation conversation,
            List<ConversationMember> members,
            UUID currentUserId
    ) {
        /*
         * Last message jest potrzebna do listy rozmów.
         *
         * W tej uproszczonej wersji status ostatniej wiadomości ustawiamy jako SENT.
         * Jeśli UI wymaga dokładnego statusu, można użyć logiki podobnej do MessageService.
         */
        MessageResponse lastMessage = conversation.getLastMessage() == null
                ? null
                : MessageResponse.from(
                conversation.getLastMessage(),
                MessageStatus.SENT,
                null,
                null
        );

        /*
         * Szukamy członkostwa aktualnego użytkownika.
         *
         * Jeśli go nie ma, nie możemy zbudować odpowiedzi,
         * bo użytkownik nie powinien widzieć tej konwersacji.
         */
        ConversationMember currentMember = members.stream()
                .filter(member -> member.getUser().getId().equals(currentUserId))
                .findFirst()
                .orElseThrow(() -> new ForbiddenException("You are not a member of this conversation"));

        /*
         * unreadCount liczymy na podstawie receiptów,
         * które nie mają ustawionego readAt dla aktualnego użytkownika.
         */
        long unreadCount =
                receiptRepository.countByConversationIdAndRecipientIdAndReadAtIsNull(
                        conversation.getId(),
                        currentUserId
                );

        return new ConversationResponse(
                conversation.getId(),
                conversation.getType(),
                conversation.getTitle(),
                conversation.getCreatedBy() == null ? null : conversation.getCreatedBy().getId(),
                currentMember.getRole(),
                members.stream()
                        .map(ConversationMemberResponse::from)
                        .toList(),
                lastMessage,
                unreadCount,
                currentMember.getLastReadAt(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }
}