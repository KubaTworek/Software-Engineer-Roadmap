package pl.jakubtworek.chatsystem.blocking;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.common.BadRequestException;
import pl.jakubtworek.chatsystem.common.ForbiddenException;
import pl.jakubtworek.chatsystem.common.NotFoundException;
import pl.jakubtworek.chatsystem.user.AppUser;
import pl.jakubtworek.chatsystem.user.UserRepository;

import java.util.List;
import java.util.UUID;

/**
 * Serwis odpowiedzialny za blokady między użytkownikami.
 *
 * Blokada jest relacją kierunkową:
 * - blocker blokuje blocked.
 *
 * Przykład:
 * jeśli User A blokuje User B, to relacja wygląda tak:
 * A -> B.
 *
 * W praktyce aplikacji czatu blokada wpływa na:
 * - tworzenie rozmów 1:1,
 * - wysyłanie wiadomości,
 * - potencjalnie presence, zaproszenia do grup i widoczność profilu.
 *
 * Ten serwis jest używany m.in. przez:
 * - BlockingController,
 * - ConversationService,
 * - MessageService.
 */
@Service
public class BlockingService {

    /**
     * Repozytorium relacji blokad.
     *
     * Odpowiada za zapis, usuwanie i sprawdzanie,
     * czy istnieje relacja blocker -> blocked.
     */
    private final BlockedUserRepository blockedUserRepository;

    /**
     * Repozytorium użytkowników.
     *
     * Potrzebne do sprawdzenia, czy użytkownik blokujący i blokowany istnieją.
     */
    private final UserRepository userRepository;

    public BlockingService(
            BlockedUserRepository blockedUserRepository,
            UserRepository userRepository
    ) {
        this.blockedUserRepository = blockedUserRepository;
        this.userRepository = userRepository;
    }

    /**
     * Blokuje wskazanego użytkownika.
     *
     * currentUserId — użytkownik wykonujący blokadę.
     * targetUserId — użytkownik, który ma zostać zablokowany.
     *
     * Operacja jest praktycznie idempotentna:
     * jeśli blokada już istnieje, zwracamy istniejący rekord,
     * zamiast tworzyć duplikat albo rzucać błąd.
     */
    @Transactional
    public BlockedUserResponse block(UUID currentUserId, UUID targetUserId) {

        /*
         * Nie pozwalamy blokować samego siebie.
         *
         * Taka relacja nie ma sensu produktowo
         * i mogłaby komplikować logikę wysyłania wiadomości.
         */
        if (currentUserId.equals(targetUserId)) {
            throw new BadRequestException("Cannot block yourself");
        }

        /*
         * Sprawdzamy, czy użytkownik blokujący istnieje.
         *
         * W normalnym flow currentUserId pochodzi z JWT,
         * ale nadal warto mieć twardą walidację na poziomie serwisu.
         */
        AppUser blocker = userRepository.findById(currentUserId)
                .orElseThrow(() -> new NotFoundException("Current user not found"));

        /*
         * Sprawdzamy, czy użytkownik docelowy istnieje.
         */
        AppUser blocked = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        /*
         * Najpierw próbujemy znaleźć istniejącą blokadę.
         *
         * Dzięki temu ponowne wywołanie:
         * POST /api/blocks/{sameUserId}
         *
         * zwróci istniejącą blokadę zamiast tworzyć duplikat.
         */
        return blockedUserRepository
                .findByBlockerIdAndBlockedId(currentUserId, targetUserId)
                .map(BlockedUserResponse::from)
                .orElseGet(() -> {
                    try {
                        /*
                         * Tworzymy nową relację blokady:
                         * blocker -> blocked.
                         */
                        return BlockedUserResponse.from(
                                blockedUserRepository.save(
                                        new BlockedUser(blocker, blocked)
                                )
                        );
                    } catch (DataIntegrityViolationException ex) {
                        /*
                         * Obsługa race condition.
                         *
                         * Dwa równoległe requesty mogą próbować utworzyć tę samą blokadę.
                         * Jeden zapis się uda, drugi uderzy w unique constraint.
                         *
                         * Zamiast zwracać 500, ponownie odczytujemy istniejącą blokadę
                         * i zwracamy ją klientowi.
                         */
                        return blockedUserRepository
                                .findByBlockerIdAndBlockedId(currentUserId, targetUserId)
                                .map(BlockedUserResponse::from)
                                .orElseThrow(() -> ex);
                    }
                });
    }

    /**
     * Usuwa blokadę użytkownika.
     *
     * Operacja jest idempotentna:
     * jeśli blokada nie istnieje, metoda nic nie robi.
     *
     * Usuwana jest tylko relacja:
     * currentUserId -> targetUserId.
     *
     * Jeśli targetUserId blokuje currentUserId, ta odwrotna blokada pozostaje bez zmian.
     */
    @Transactional
    public void unblock(UUID currentUserId, UUID targetUserId) {
        blockedUserRepository
                .findByBlockerIdAndBlockedId(currentUserId, targetUserId)
                .ifPresent(blockedUserRepository::delete);
    }

    /**
     * Pobiera listę użytkowników zablokowanych przez aktualnego użytkownika.
     *
     * Używane przez:
     * GET /api/blocks
     *
     * Zwracane są tylko blokady, gdzie blockerId == currentUserId.
     */
    @Transactional(readOnly = true)
    public List<BlockedUserResponse> getMyBlockedUsers(UUID currentUserId) {
        return blockedUserRepository
                .findByBlockerId(currentUserId)
                .stream()
                .map(BlockedUserResponse::from)
                .toList();
    }

    /**
     * Sprawdza, czy między dwoma użytkownikami istnieje blokada w dowolną stronę.
     *
     * To jedna z najważniejszych metod tego serwisu.
     *
     * Używana jest jako guard w innych częściach aplikacji, np.:
     * - przed utworzeniem rozmowy 1:1,
     * - przed wysłaniem wiadomości.
     *
     * Jeśli:
     * - recipient blokuje sendera,
     * - albo sender blokuje recipienta,
     *
     * to operacja powinna być zablokowana.
     */
    public void ensureNotBlockedEitherWay(UUID senderId, UUID recipientId) {

        /*
         * Sprawdzamy oba kierunki relacji:
         *
         * recipientId -> senderId
         * senderId -> recipientId
         *
         * Dzięki temu wiadomość nie przejdzie zarówno wtedy,
         * gdy odbiorca zablokował nadawcę,
         * jak i wtedy, gdy nadawca zablokował odbiorcę.
         */
        if (blockedUserRepository.existsByBlockerIdAndBlockedIdOrBlockerIdAndBlockedId(
                recipientId,
                senderId,
                senderId,
                recipientId
        )) {
            throw new ForbiddenException("Message blocked by user privacy settings");
        }
    }
}