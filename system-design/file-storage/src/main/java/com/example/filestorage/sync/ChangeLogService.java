package com.example.filestorage.sync;

import com.example.filestorage.sharing.ResourceType;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Serwis odpowiedzialny za changelog zmian w plikach i folderach.
 *
 * Changelog jest podstawą synchronizacji klienta.
 * Dzięki niemu aplikacja kliencka nie musi za każdym razem pobierać całego drzewa plików.
 *
 * Klient zapamiętuje ostatni znany cursor, czyli ID ostatnio odebranej zmiany,
 * a potem pyta backend: "daj mi wszystko po tym ID".
 */
@Service
public class ChangeLogService {

    /**
     * Repozytorium wpisów changeloga.
     * Każdy wpis reprezentuje jedną zmianę w zasobie, np. FILE_CREATED, FILE_DELETED.
     */
    private final ChangeLogRepository repository;

    public ChangeLogService(ChangeLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Zapisuje pojedynczą zmianę w changelogu.
     *
     * Ta metoda powinna być wołana po operacjach modyfikujących stan aplikacji:
     * - upload pliku,
     * - rename pliku/folderu,
     * - move pliku/folderu,
     * - delete,
     * - restore,
     * - zmiana uprawnień,
     * - utworzenie nowej wersji pliku.
     *
     * actorUserId:
     * użytkownik, który wykonał operację.
     *
     * ownerId:
     * właściciel przestrzeni plików, dla której zmiana ma być widoczna w synchronizacji.
     * To ważne przy folderach współdzielonych — aktor może być inny niż właściciel zasobu.
     *
     * resourceType:
     * typ zasobu, np. FILE albo FOLDER.
     *
     * resourceId:
     * ID zmienionego pliku albo folderu.
     *
     * operation:
     * nazwa operacji, np. FILE_CREATED, FILE_RENAMED, FILE_DELETED.
     *
     * payload:
     * dodatkowe dane zmiany w JSON stringu, np. nowa nazwa albo parentFolderId.
     */
    @Transactional
    public void record(UUID actorUserId,
                       UUID ownerId,
                       ResourceType resourceType,
                       UUID resourceId,
                       String operation,
                       String payload) {
        repository.save(
                new ChangeLog(
                        actorUserId,
                        ownerId,
                        resourceType,
                        resourceId,
                        operation,
                        payload
                )
        );
    }

    /**
     * Zwraca zmiany dla danego właściciela od wskazanego cursora.
     *
     * To endpointowa logika synchronizacji:
     * klient wysyła ostatni znany cursor,
     * backend zwraca zmiany o ID większym niż cursor.
     *
     * Przykład:
     * - klient ma cursor = 100,
     * - backend zwraca wpisy 101, 102, 103,
     * - nextCursor = 103,
     * - klient zapisuje 103 lokalnie.
     */
    @Transactional(readOnly = true)
    public SyncChangesResponse changes(UUID ownerId, long cursor, int limit) {
        /*
         * Limit jest zabezpieczony przed zbyt małą i zbyt dużą wartością.
         *
         * Minimum: 1
         * Maksimum: 500
         *
         * Dzięki temu klient nie może wymusić zwrócenia ogromnej liczby zmian
         * w jednym requestcie.
         */
        int safeLimit = Math.min(Math.max(limit, 1), 500);

        /*
         * Pobieramy safeLimit + 1 rekordów.
         *
         * To prosty trik do sprawdzenia, czy istnieje kolejna strona wyników.
         * Jeśli prosimy o 501 rekordów przy limicie 500 i dostajemy 501,
         * to znaczy, że hasMore = true.
         */
        List<ChangeLog> rows = repository.findAllByOwnerIdAndIdGreaterThanOrderByIdAsc(
                ownerId,
                Math.max(cursor, 0),
                PageRequest.of(0, safeLimit + 1)
        );

        /*
         * hasMore informuje klienta, że powinien wykonać kolejne żądanie
         * z nextCursor, bo nie pobrał jeszcze wszystkich zmian.
         */
        boolean hasMore = rows.size() > safeLimit;

        /*
         * Do odpowiedzi zwracamy maksymalnie safeLimit rekordów.
         * Nadmiarowy rekord służył tylko do wykrycia hasMore.
         */
        List<ChangeLog> page = hasMore ? rows.subList(0, safeLimit) : rows;

        /*
         * nextCursor wskazuje ID ostatniego zwróconego wpisu.
         *
         * Jeśli nie ma żadnych zmian, cursor pozostaje taki sam.
         * Dzięki temu klient może bezpiecznie ponawiać polling.
         */
        Long nextCursor = page.isEmpty()
                ? Math.max(cursor, 0)
                : page.getLast().getId();

        /*
         * Zwracamy:
         * - listę zmian,
         * - cursor do kolejnego requestu,
         * - informację, czy są kolejne zmiany do pobrania.
         */
        return new SyncChangesResponse(
                page.stream()
                        .map(ChangeLogResponse::from)
                        .toList(),
                nextCursor,
                hasMore
        );
    }
}