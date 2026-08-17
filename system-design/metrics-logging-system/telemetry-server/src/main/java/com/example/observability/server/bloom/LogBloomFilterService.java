package com.example.observability.server.bloom;

import com.example.observability.server.model.LogEventDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Serwis budujący i odpytywujący bloom filtery dla logów.
 *
 * Cel:
 * - ograniczyć koszt zapytań typu "message contains X",
 * - uniknąć pełnego skanowania logów, jeśli wiadomo, że szukany token
 *   na pewno nie występuje w danym zakresie czasu.
 *
 * Bloom filter może dać false positive, ale nie powinien dawać false negative.
 *
 * Oznacza to:
 * - jeśli zwróci true: token może istnieć, trzeba wykonać normalne query,
 * - jeśli zwróci false: token na pewno nie istnieje, można pominąć scan.
 *
 * W tym systemie bloom filter jest budowany per:
 * - tenant,
 * - service,
 * - level,
 * - godzinny bucket czasu.
 */
@Service
public class LogBloomFilterService {

    /**
     * Regex do dzielenia message na tokeny.
     *
     * Zachowuje znaki często spotykane w logach:
     * - litery,
     * - cyfry,
     * - underscore,
     * - kropkę,
     * - slash,
     * - dwukropek,
     * - myślnik.
     *
     * Dzięki temu tokenem może być np.:
     * - payment.timeout
     * - /api/orders
     * - trace-123
     * - 500
     */
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-zA-Z0-9_./:-]+");

    /**
     * Rozmiar bitsetu bloom filtera.
     *
     * Większy rozmiar oznacza:
     * - mniej false positives,
     * - większy zapis w tabeli logs_bloom_filters.
     *
     * 8192 bitów to lekki kompromis dla MVP.
     */
    private static final int BLOOM_SIZE = 8192;

    /**
     * Liczba funkcji hashujących.
     *
     * Więcej hashy może zmniejszyć false positives,
     * ale zwiększa koszt dodawania i sprawdzania tokenów.
     */
    private static final int HASH_COUNT = 5;

    /**
     * Dostęp do bazy.
     *
     * Ten serwis zapisuje i odczytuje bloom filtery z tabeli:
     * logs_bloom_filters.
     */
    private final JdbcTemplate jdbc;

    public LogBloomFilterService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Buduje bloom filtery dla batcha logów.
     *
     * Wywoływane po stronie pipeline'u ingestu logów.
     *
     * Przepływ:
     * 1. Dzieli logi na grupy po tenant/service/level/godzina.
     * 2. Dla każdej grupy tworzy osobny bloom filter.
     * 3. Tokenizuje message każdego loga.
     * 4. Dodaje tokeny do odpowiedniego filtera.
     * 5. Zapisuje zakodowany filter do bazy.
     *
     * Dzięki temu query planner może później sprawdzić:
     * "czy w danym bucketcie może istnieć szukany token?"
     */
    public void buildForBatch(String tenantId, List<LogEventDto> logs) {
        Map<Key, SimpleBloomFilter> filters = new HashMap<>();

        for (LogEventDto log : logs) {
            /*
             * Jeśli log nie ma timestampu, używamy aktualnego czasu.
             * W praktyce timestamp powinien być już znormalizowany wcześniej
             * w IngestController albo consumerze.
             */
            Instant ts = log.getTimestamp() == null
                    ? Instant.now()
                    : log.getTimestamp();

            /*
             * Bloom filter jest budowany per godzina UTC.
             *
             * Bucket godzinowy ogranicza liczbę danych sprawdzanych przy query
             * i dobrze pasuje do typowego podziału logów po czasie.
             */
            Instant bucket = ts
                    .atZone(ZoneOffset.UTC)
                    .truncatedTo(ChronoUnit.HOURS)
                    .toInstant();

            Key key = new Key(
                    tenantId,
                    safe(log.getService()),
                    safe(log.getLevel()).toUpperCase(),
                    bucket
            );

            SimpleBloomFilter filter = filters.computeIfAbsent(
                    key,
                    ignored -> new SimpleBloomFilter(BLOOM_SIZE, HASH_COUNT)
            );

            /*
             * Do bloom filtera trafiają tokeny z message.
             *
             * Nie indeksujemy całego JSON-a ani attributes.
             * To jest świadomy kompromis MVP: szybciej i taniej,
             * ale mniej kompletne wyszukiwanie.
             */
            for (String token : tokenize(log.getMessage())) {
                filter.add(token);
            }
        }

        /*
         * Zapisujemy jeden bloom filter na każdy bucket tenant/service/level/hour.
         *
         * bloom_bits to zakodowana reprezentacja bitsetu.
         */
        for (var entry : filters.entrySet()) {
            Key key = entry.getKey();

            jdbc.update("""
                    INSERT INTO logs_bloom_filters
                    (tenant_id, service, level, bucket_start, bloom_size, hash_count, bloom_bits)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    key.tenantId,
                    key.service,
                    key.level,
                    Timestamp.from(key.bucketStart),
                    BLOOM_SIZE,
                    HASH_COUNT,
                    entry.getValue().encode()
            );
        }
    }

    /**
     * Sprawdza, czy dany token może występować w logach dla podanego zakresu.
     *
     * Ta metoda jest używana przez QueryPlanner.
     *
     * Zwraca:
     * - true, jeśli token może istnieć albo nie mamy wystarczającej informacji,
     * - false, jeśli bloom filtery jednoznacznie wskazują, że tokenu nie ma.
     *
     * Najważniejsza zasada:
     * przy braku filterów zwracamy true, żeby nie zgubić prawdziwych wyników.
     */
    public boolean mightHaveTerm(
            String tenantId,
            String service,
            String level,
            Instant start,
            Instant end,
            String term
    ) {
        /*
         * Brak termu albo zakresu czasu oznacza, że nie można bezpiecznie
         * użyć bloom filtera do pominięcia scanowania.
         */
        if (term == null || term.isBlank()) {
            return true;
        }

        if (start == null || end == null) {
            return true;
        }

        /*
         * Pobieramy bloom filtery z zakresu czasu i opcjonalnych filtrów:
         * - service,
         * - level.
         *
         * Konstrukcja (? = '' OR service = ?) pozwala jednym SQL-em obsłużyć
         * przypadek z filtrem i bez filtra.
         */
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT bloom_size, hash_count, bloom_bits
                FROM logs_bloom_filters
                WHERE tenant_id = ?
                  AND (? = '' OR service = ?)
                  AND (? = '' OR level = ?)
                  AND bucket_start >= toStartOfHour(?)
                  AND bucket_start <= toStartOfHour(?)
                """,
                tenantId,
                safe(service),
                safe(service),
                safe(level).toUpperCase(),
                safe(level).toUpperCase(),
                Timestamp.from(start),
                Timestamp.from(end)
        );

        /*
         * Jeżeli nie mamy żadnego bloom filtera, nie możemy stwierdzić,
         * że token nie istnieje.
         *
         * Dlatego zwracamy true i pozwalamy normalnemu query przeskanować dane.
         * To chroni przed false negative przy niepełnym indeksie.
         */
        if (rows.isEmpty()) {
            return true;
        }

        /*
         * Dla zapytania tekstowego bierzemy pierwszy token.
         *
         * Przykład:
         * "payment timeout error" -> "payment"
         *
         * To jest uproszczenie.
         * Produkcyjnie warto sprawdzać wszystkie tokeny i mieć strategię AND/OR.
         */
        String normalized = firstToken(term);

        /*
         * Jeżeli którykolwiek bloom filter twierdzi, że token może istnieć,
         * zwracamy true.
         *
         * Dopiero gdy wszystkie filtery zwrócą false, można bezpiecznie
         * pominąć hot scan.
         */
        return rows.stream().anyMatch(row -> SimpleBloomFilter
                .decode(
                        (String) row.get("bloom_bits"),
                        ((Number) row.get("bloom_size")).intValue(),
                        ((Number) row.get("hash_count")).intValue()
                )
                .mightContain(normalized)
        );
    }

    /**
     * Tokenizuje wiadomość loga.
     *
     * Zasady:
     * - null -> pusta lista,
     * - tekst jest zamieniany na lowercase,
     * - tokeny krótsze niż 3 znaki są pomijane,
     * - maksymalnie 128 tokenów z jednego message.
     *
     * Limit 128 chroni przed bardzo długimi logami, które mogłyby
     * nadmiernie obciążyć bloom filter i zwiększyć false positives.
     */
    public List<String> tokenize(String message) {
        if (message == null) {
            return List.of();
        }

        return Arrays.stream(TOKEN_SPLIT.split(message.toLowerCase()))
                .filter(s -> s.length() >= 3)
                .limit(128)
                .toList();
    }

    /**
     * Zwraca pierwszy token z tekstu query.
     *
     * Używane w mightHaveTerm().
     *
     * Jeśli tokenizacja nic nie zwróci, fallbackiem jest lowercase całego tekstu.
     */
    private String firstToken(String text) {
        return tokenize(text)
                .stream()
                .findFirst()
                .orElse(text.toLowerCase());
    }

    /**
     * Normalizuje null do pustego stringa.
     *
     * Używane dla service/level, żeby query SQL mogło obsłużyć brak filtra.
     */
    private String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * Klucz grupowania bloom filtera.
     *
     * Jeden filter odpowiada dokładnie jednej kombinacji:
     * - tenantId,
     * - service,
     * - level,
     * - bucketStart.
     */
    private record Key(
            String tenantId,
            String service,
            String level,
            Instant bucketStart
    ) {
    }
}