# Warunkowy zapis, idempotentne przyjęcie i wynik zadania

[Lista laboratoriów](README.md) · [API](../catalog/16-api/README.md) ·
[Bezpieczeństwo](../catalog/13-security/README.md)

**Modele w tym dokumencie:** [ConditionalDocuments](04-api.md#model-conditionaldocuments),
[LongRunningOperations](04-api.md#model-longrunningoperations). **Scenariusze poprawności:**
[dane, operacje i oczekiwane wyniki](04-api.md#scenariusze-poprawności).

## Tożsamość, zakres i wersja

Key zawiera tenant oraz id. Dlatego d1 u A i d1 u B są niezależnymi dokumentami. Próba dostępu do cudzego
rekordu daje NotFound, tak samo jak brak rekordu; model nie ujawnia istnienia zasobu u innego tenanta.

Scope jest wejściem zaufanego adaptera, a nie dowodem uwierzytelnienia. Nie ma tu walidacji JWT. Adapter nie
powinien przyjmować tenant z dowolnego parametru klienta. Subject jest częścią kontekstu, ale wszyscy
użytkownicy tenanta mają tu jednakowe prawa: model nie implementuje RBAC ani ACL pojedynczego dokumentu.

## Utracona aktualizacja i ETag

Dwóch klientów odczytuje `"v0"`. Pierwszy wykonuje replace i publikuje `"v1"`. Drugi nadal wysyła `"v0"`, więc
otrzymuje PreconditionFailed, które adapter HTTP może odwzorować na 412. Pierwsza treść pozostaje zachowana.
Ten sam monitor obejmuje porównanie i zapis; dwa osobne synchronizowane get/set nie chroniłyby całej decyzji
przed wyścigiem.

Fragment testu JUnit, opisany razem z modelem poniżej:

```java
var store = new ConditionalDocuments();
var original = store.create(tenantA, "d1", "original");
store.replace(tenantA, "d1", original.etag(), "first writer");

assertThrows(ConditionalDocuments.PreconditionFailed.class,
        () -> store.replace(tenantA, "d1", original.etag(), "stale writer"));
assertEquals("first writer", store.get(tenantA, "d1").body());
```

ETag nie jest credentialem. Jego znajomość nie omija scope. Demo przyjmuje jedną dokładną wartość strong: weak
ETag i brak wartości są odrzucane. Nie implementuje pełnej gramatyki If-Match, list walidatorów, gwiazdki ani
innych warunków HTTP.

## Długi eksport: przyjęcie nie oznacza zakończenia

Submit atomowo w zakresie monitora tworzy wpis deduplikacji i operację ACCEPTED. Klucz zawiera tenant oraz
key. Ponowienie tego samego payloadu wraca do tej samej operacji; inny payload pod tym kluczem jest błędem. U
drugiego tenanta taki sam key identyfikuje odrębne żądanie. Polling również weryfikuje zakres tenanta.

| Stan | Legalny następnik | Kontrprzykład |
| --- | --- | --- |
| ACCEPTED | RUNNING | pominięcie wykonania prosto do SUCCEEDED |
| RUNNING | SUCCEEDED lub FAILED | powrót do ACCEPTED |
| SUCCEEDED, FAILED | brak | spóźniony wynik przepisujący terminalny stan |

Ponowiony submit po zakończeniu zwraca aktualny stan tej samej operacji, a nie identyczną kopię pierwszej
odpowiedzi 202. To idempotencja utworzenia procesu i stabilnego uchwytu. Advance jest portem zaufanego
workera: udostępnienie go dowolnemu użytkownikowi pozwoliłoby fałszować wynik.

## Koszt i ograniczenia

Lookup map ma oczekiwane O(1), ale brak retencji daje rosnącą pamięć. Modele nie mają prawdziwego HTTP,
workerów, kolejki, anulowania ani restartu. Dwie zmiany pod jednym monitorem nie stanowią trwałego commitu
przy awarii JVM. Produkcyjne przyjęcie wymaga utrwalenia uchwytu i zamiaru wykonania, np. z Outbox w jednej
transakcji; inaczej ACCEPTED może nigdy nie dotrzeć do workera.

## Model ConditionalDocuments

Model dokumentacyjny Java 21. Poniższy blok zawiera pełne typy, importy, komentarze i mechanizm przykładu. Nie
jest osobnym plikiem źródłowym ani uruchamianą aplikacją; ograniczenia i przebieg opisano w tym dokumencie.

<details>
<summary>Rozwiń pełny przykład: ConditionalDocuments</summary>

```java
package pl.jakubtworek.patterns.labs.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Model HTTP If-Match i optimistic locking bez frameworka oraz serwera HTTP.
 */
public final class ConditionalDocuments {
    public record Scope(String tenant, String subject) {
        public Scope {
            if (tenant == null || tenant.isBlank() || subject == null || subject.isBlank()) {
                throw new IllegalArgumentException("Authenticated tenant and subject required");
            }
        }
    }

    private record Key(String tenant, String id) {
    }

    public record Document(String id, String body, long version) {
        public String etag() {
            // ETag jest nieprzezroczystym walidatorem, nie tokenem autoryzacji.
            return "\"v" + version + "\"";
        }
    }

    public static final class NotFound extends RuntimeException {
    }

    public static final class PreconditionFailed extends RuntimeException {
    }

    private final Map<Key, Document> documents = new HashMap<>();

    public synchronized Document create(Scope scope, String id, String body) {
        Objects.requireNonNull(body);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Document ID required");
        }
        var key = new Key(scope.tenant(), id);
        if (documents.containsKey(key)) {
            throw new IllegalStateException("Document already exists");
        }
        var document = new Document(id, body, 0);
        documents.put(key, document);
        return document;
    }

    public synchronized Document get(Scope scope, String id) {
        Document document = documents.get(new Key(scope.tenant(), id));
        if (document == null) {
            // Nie ujawniamy, czy taki identyfikator istnieje u innego tenanta.
            throw new NotFound();
        }
        return document;
    }

    public synchronized Document replace(Scope scope, String id, String ifMatch, String body) {
        Objects.requireNonNull(body);
        Document current = get(scope, id);
        if (!current.etag().equals(ifMatch)) {
            throw new PreconditionFailed();
        }
        var replacement = new Document(id, body, Math.incrementExact(current.version()));
        // Porównanie i zapis są pod tym samym monitorem. Rozdzielenie tych kroków dałoby lost update.
        documents.put(new Key(scope.tenant(), id), replacement);
        return replacement;
    }
}
```

</details>

## Model LongRunningOperations

Model dokumentacyjny Java 21. Poniższy blok zawiera pełne typy, importy, komentarze i mechanizm przykładu. Nie
jest osobnym plikiem źródłowym ani uruchamianą aplikacją; ograniczenia i przebieg opisano w tym dokumencie.

<details>
<summary>Rozwiń pełny przykład: LongRunningOperations</summary>

```java
package pl.jakubtworek.patterns.labs.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Async Request–Reply, idempotentne przyjęcie oraz jawna maszyna stanów zadania.
 */
public final class LongRunningOperations {
    public enum Status {
        ACCEPTED, RUNNING, SUCCEEDED, FAILED
    }

    private record RequestKey(String tenant, String key) {
    }

    private record Accepted(String payload, String operationId) {
    }

    public record Operation(String id, String tenant, Status status, String detail) {
        public String location() {
            return "/operations/" + id;
        }
    }

    private final Map<RequestKey, Accepted> accepted = new HashMap<>();
    private final Map<String, Operation> operations = new HashMap<>();
    private long nextId;

    public synchronized Operation submit(String tenant, String key, String payload) {
        requireText(tenant);
        requireText(key);
        requireText(payload);
        var scopedKey = new RequestKey(tenant, key);
        Accepted previous = accepted.get(scopedKey);
        if (previous != null) {
            if (!previous.payload().equals(payload)) {
                throw new IllegalArgumentException("Idempotency key reused with a different request");
            }
            return get(tenant, previous.operationId());
        }
        String id = Long.toString(Math.incrementExact(nextId));
        var operation = new Operation(id, tenant, Status.ACCEPTED, "");
        // Obie mapy są zmieniane w jednej sekcji krytycznej; model nie symuluje trwałego commitu.
        operations.put(id, operation);
        accepted.put(scopedKey, new Accepted(payload, id));
        nextId++;
        return operation;
    }

    public synchronized Operation get(String tenant, String id) {
        Operation operation = operations.get(id);
        if (operation == null || !operation.tenant().equals(tenant)) {
            throw new ConditionalDocuments.NotFound();
        }
        return operation;
    }

    public synchronized Operation advance(String tenant, String id, Status target, String detail) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(detail);
        Operation current = get(tenant, id);
        boolean allowed = switch(current.status()) {
            case ACCEPTED ->
                target == Status.RUNNING;
            case RUNNING ->
                target == Status.SUCCEEDED || target == Status.FAILED;
            case SUCCEEDED, FAILED ->
                false;
        };
        if (!allowed) {
            throw new IllegalStateException("Illegal operation transition");
        }
        var changed = new Operation(id, tenant, target, detail);
        operations.put(id, changed);
        return changed;
    }

    private static void requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Nonblank value required");
        }
    }
}
```

</details>

## Scenariusze poprawności

Scenariusze poprawności w notacji JUnit. Pokazują dane, działanie i oczekiwany stan. Są materiałem
dokumentacyjnym; moduł nie ma zależności JUnit ani automatycznego uruchamiania tych asercji.

<details>
<summary>Rozwiń pełny przykład: ApiLabTest</summary>

```java
package pl.jakubtworek.patterns.labs;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.patterns.labs.api.ConditionalDocuments;
import pl.jakubtworek.patterns.labs.api.LongRunningOperations;

class ApiLabTest {
    private final ConditionalDocuments.Scope tenantA = new ConditionalDocuments.Scope("A", "alice");
    private final ConditionalDocuments.Scope tenantB = new ConditionalDocuments.Scope("B", "bob");

    @Test
    void etagRejectsStaleWriterWithoutOverwritingFirstUpdate() {
        var store = new ConditionalDocuments();
        var original = store.create(tenantA, "d1", "original");
        var changed = store.replace(tenantA, "d1", original.etag(), "first writer");
        assertEquals("\"v1\"", changed.etag());
        assertThrows(ConditionalDocuments.PreconditionFailed.class,
                () -> store.replace(tenantA, "d1", original.etag(), "stale writer"));
        assertEquals("first writer", store.get(tenantA, "d1").body());
    }

    @Test
    void sameIdentifierDoesNotConnectDataFromDifferentTenants() {
        var store = new ConditionalDocuments();
        store.create(tenantA, "d1", "private-A");
        assertThrows(ConditionalDocuments.NotFound.class, () -> store.get(tenantB, "d1"));
        assertThrows(ConditionalDocuments.NotFound.class, () -> store.replace(tenantB, "d1", "\"v0\"", "attack"));
        store.create(tenantB, "d1", "private-B");
        assertEquals("private-A", store.get(tenantA, "d1").body());
        assertEquals("private-B", store.get(tenantB, "d1").body());
    }

    @Test
    void weakOrMissingValidatorDoesNotPassExactIfMatchContract() {
        var store = new ConditionalDocuments();
        store.create(tenantA, "d1", "body");
        assertThrows(ConditionalDocuments.PreconditionFailed.class,
                () -> store.replace(tenantA, "d1", "W/\"v0\"", "weak"));
        assertThrows(ConditionalDocuments.PreconditionFailed.class,
                () -> store.replace(tenantA, "d1", null, "no validator"));
    }

    @Test
    void retryOfSubmissionReturnsSameOperationInsteadOfStartingAnother() {
        var operations = new LongRunningOperations();
        var first = operations.submit("A", "key1", "monthly-report");
        var retried = operations.submit("A", "key1", "monthly-report");
        assertEquals(first, retried);
        assertEquals("/operations/1", first.location());
        assertEquals(LongRunningOperations.Status.ACCEPTED, first.status());
        assertThrows(IllegalArgumentException.class, () -> operations.submit("A", "key1", "different-report"));
    }

    @Test
    void terminalOperationCannotBeRestartedOrChangedByLateResult() {
        var operations = new LongRunningOperations();
        var operation = operations.submit("A", "key1", "report");
        operations.advance("A", operation.id(), LongRunningOperations.Status.RUNNING, "");
        operations.advance("A", operation.id(), LongRunningOperations.Status.SUCCEEDED, "report-42");
        assertThrows(IllegalStateException.class,
                () -> operations.advance("A", operation.id(), LongRunningOperations.Status.FAILED, "late timeout"));
        assertEquals("report-42", operations.get("A", operation.id()).detail());
        assertEquals(LongRunningOperations.Status.SUCCEEDED, operations.submit("A", "key1", "report").status());
    }

    @Test
    void pollingRequiresTenantScopeAndIdempotencyKeyIsTenantLocal() {
        var operations = new LongRunningOperations();
        var a = operations.submit("A", "key1", "report");
        var b = operations.submit("B", "key1", "report");
        assertNotEquals(a.id(), b.id());
        assertThrows(ConditionalDocuments.NotFound.class, () -> operations.get("B", a.id()));
        assertThrows(IllegalStateException.class,
                () -> operations.advance("A", a.id(), LongRunningOperations.Status.SUCCEEDED, "skipped running"));
    }
}
```

</details>
