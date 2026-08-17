package com.example.filestorage.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Serwis biznesowy odpowiedzialny za wyszukiwanie plików i folderów.
 *
 * Nie przeszukuje bezpośrednio tabel files/folders.
 * Korzysta z pomocniczej tabeli SearchIndex, która jest aktualizowana
 * przez SearchIndexService po zmianach w plikach i folderach.
 *
 * Dzięki temu wyszukiwanie jest prostsze i szybsze,
 * bo działa na jednym ujednoliconym indeksie zasobów.
 */
@Service
public class SearchService {

    /**
     * Repozytorium indeksu wyszukiwania.
     *
     * Odpowiada za faktyczne zapytanie do bazy,
     * np. po ownerId, nazwie, typie zasobu lub metadanych.
     */
    private final SearchIndexRepository repository;

    public SearchService(SearchIndexRepository repository) {
        this.repository = repository;
    }

    /**
     * Wyszukuje zasoby należące do danego właściciela.
     *
     * ownerId:
     * identyfikator przestrzeni plików, w której szukamy.
     *
     * query:
     * fraza wpisana przez użytkownika.
     *
     * page i size:
     * parametry paginacji wyników.
     *
     * Ważne:
     * obecna implementacja filtruje po ownerId.
     * To wystarcza dla prywatnych plików właściciela, ale nie obejmuje pełnego modelu
     * "shared with me", gdzie użytkownik może mieć dostęp do cudzych plików.
     *
     * Jeśli wyszukiwarka ma obsługiwać udostępnione zasoby, trzeba rozszerzyć ją
     * o filtrowanie przez ACL/permissions albo osobny indeks dostępów.
     */
    @Transactional(readOnly = true)
    public SearchResponse search(UUID ownerId, String query, int page, int size) {
        /*
         * Normalizacja query.
         * Null zamieniamy na pusty string, a whitespace z początku i końca usuwamy.
         *
         * Dzięki temu repository nie musi obsługiwać nulli.
         */
        String q = query == null ? "" : query.trim();

        /*
         * Zabezpieczenie paginacji:
         * - page nie może być ujemne,
         * - size musi być minimum 1,
         * - size jest ograniczone do 100.
         *
         * To chroni endpoint przed zbyt dużymi odpowiedziami
         * i kosztownymi zapytaniami do bazy.
         */
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        /*
         * Zapytanie do indeksu wyszukiwania.
         *
         * Sortujemy po updatedAt malejąco, więc najnowsze lub ostatnio odświeżone
         * wyniki pojawią się jako pierwsze.
         */
        Page<SearchIndex> result = repository.search(
                ownerId,
                q,
                PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by(Sort.Direction.DESC, "updatedAt")
                )
        );

        /*
         * Encje SearchIndex mapujemy na DTO odpowiedzi.
         * Dzięki temu API nie zwraca bezpośrednio modelu bazodanowego.
         */
        return new SearchResponse(
                result.getContent()
                        .stream()
                        .map(SearchResultResponse::from)
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}