# Zbieżność, fencing i stabilny właściciel klucza

[Lista laboratoriów](README.md) · [Spójność](../catalog/09-consistency/README.md) ·
[Skalowanie](../catalog/11-scaling/README.md)

**Modele w tym dokumencie:** [GCounter](06-distributed-data.md#model-gcounter),
[FencedResource](06-distributed-data.md#model-fencedresource),
[RendezvousRouter](06-distributed-data.md#model-rendezvousrouter). **Scenariusze poprawności:**
[dane, operacje i oczekiwane wyniki](06-distributed-data.md#scenariusze-poprawności).

## CRDT: duplikat stanu nie jest nowym przyrostem

GCounter przechowuje mapę replicaId → łączny przyrost repliki. A ma 3, B ma 2. Merge bierze maksimum każdej
składowej, a odczyt sumuje wynik do 5. Ponowne dostarczenie A nie dodaje następnych 3.

| Własność merge | Znaczenie operacyjne |
| --- | --- |
| przemienność | inna kolejność dostarczenia daje ten sam wynik |
| łączność | inne grupowanie łączenia nie zmienia wyniku |
| idempotencja | replay tej samej kopii nie zwiększa licznika |

Testy sprawdzają te relacje na wielu danych. Kontrprzykład dwóch niezależnych procesów z tym samym replicaId i
przyrostami 3 oraz 2 daje po merge 3, a nie 5. Jedna składowa wymaga jednego autora zachowującego monotoniczny
postęp również po restarcie. Algorytm nie przydziela tożsamości replik ani nie odtwarza ich dysków.

Snapshot jest niezmienny, merge kosztuje oczekiwane O(a+b), pamięć rośnie z liczbą replik. Math.addExact
odrzuca overflow; long nie ma matematycznie nieograniczonego zakresu. Brak dekrementów i usuwania replik jest
celowy. Ścisły limit sprzedaży wymaga reguły przydziału praw, nie tylko zbieżnej sumy.

## Fencing: dawny właściciel może nadal działać

A z tokenem 7 zapisuje wynik i zatrzymuje się na pauzę. B uzyskuje token 8 i zgłasza claim w store. Gdy A
wraca, store odrzuca write(7), mimo że A nadal wierzy we własny lease. Kontrola musi leżeć przy chronionym
zasobie.

Fragment scenariusza asercji w konwencji JUnit; pełny model i kontekst są dalej w tym dokumencie:

```java
var resource = new FencedResource();
resource.claim(7);
resource.write(7, "A before pause");
resource.claim(8);
resource.write(8, "B after takeover");

assertThrows(IllegalStateException.class,
        () -> resource.write(7, "A resumed too late"));
assertEquals("B after takeover", resource.read());
```

Claim wymaga tokenu ściśle większego, a write dokładnie aktualnego. Większa liczba bez wcześniejszego claim
nie daje prawa zapisu. Synchronized łączy sprawdzenie z publikacją, ale model nie implementuje lock managera,
wydawania tokenów ani trwałości highestToken. Po restarcie store musiałby zachować najwyższą epokę.

## Rendezvous hashing: zakres zmiany topologii

Każda para key–node ma stabilny score; router wybiera maksimum unsigned. Prefiks długości usuwa
niejednoznaczność konkatenacji, a sortowanie nazw stabilizuje ewentualne remisy. SHA-256 służy tu rozkładowi,
nie ukrywaniu kluczy.

Po dodaniu D stare wyniki A, B i C nie zmieniają się. Klucz pozostaje więc u dotychczasowego zwycięzcy albo
przechodzi wyłącznie do D. Test sprawdza tę własność na tysiącu kluczy. Osobny test usuwa B i potwierdza
stabilność kluczy należących do pozostałych węzłów. Nie jest to dowód idealnego wyrównania ruchu.

Koszt to O(N) hashy dla N węzłów plus rozmiar kluczy. Nie ma wag, replik, health check ani przenoszenia
danych. Router wskazuje adres; przełączenie właściciela zapisu i migracja są odrębnym protokołem. Hot key
nadal ma jednego zwycięzcę i może przeciążać go mimo dobrego rozkładu pozostałych kluczy.

## Model GCounter

Model dokumentacyjny Java 21. Poniższy blok zawiera pełne typy, importy, komentarze i mechanizm przykładu. Nie
jest osobnym plikiem źródłowym ani uruchamianą aplikacją; ograniczenia i przebieg opisano w tym dokumencie.

<details>
<summary>Rozwiń pełny przykład: GCounter</summary>

```java
package pl.jakubtworek.patterns.labs.consistency;

import java.util.HashMap;
import java.util.Map;

/**
 * Stanowy CRDT typu grow-only counter. Instancja jest niezmiennym snapshotem.
 */
public record GCounter(Map<String, Long> components) {
    public GCounter {
        components = Map.copyOf(components);
        components.forEach((replica, value) -> {
            if (replica.isBlank() || value < 0) {
                throw new IllegalArgumentException("Replica name and nonnegative component required");
            }
        });
    }

    public static GCounter empty() {
        return new GCounter(Map.of());
    }

    public GCounter increment(String localReplica, long delta) {
        if (localReplica == null || localReplica.isBlank() || delta <= 0) {
            throw new IllegalArgumentException("Replica name and positive delta required");
        }
        var updated = new HashMap<>(components);
        updated.put(localReplica, Math.addExact(updated.getOrDefault(localReplica, 0L), delta));
        return new GCounter(updated);
    }

    public GCounter merge(GCounter other) {
        var merged = new HashMap<>(components);
        // Maksimum zamiast sumy: ta sama kopia stanu może dotrzeć dowolnie wiele razy.
        other.components.forEach((replica, value) -> merged.merge(replica, value, Math::max));
        return new GCounter(merged);
    }

    public long value() {
        long total = 0;
        for (long component : components.values()) {
            total = Math.addExact(total, component);
        }
        return total;
    }
}
```

</details>

## Model FencedResource

Model dokumentacyjny Java 21. Poniższy blok zawiera pełne typy, importy, komentarze i mechanizm przykładu. Nie
jest osobnym plikiem źródłowym ani uruchamianą aplikacją; ograniczenia i przebieg opisano w tym dokumencie.

<details>
<summary>Rozwiń pełny przykład: FencedResource</summary>

```java
package pl.jakubtworek.patterns.labs.consistency;

import java.util.Objects;

/**
 * Model store egzekwującego monotonny token własności, bez rozproszonego lock managera.
 */
public final class FencedResource {
    private long highestToken;
    private String value = "";

    public synchronized void claim(long token) {
        if (token <= highestToken) {
            throw new IllegalStateException("Ownership token must increase");
        }
        highestToken = token;
    }

    public synchronized void write(long token, String replacement) {
        Objects.requireNonNull(replacement);
        if (token != highestToken || token <= 0) {
            throw new IllegalStateException("Stale or unclaimed owner");
        }
        value = replacement;
    }

    public synchronized String read() {
        return value;
    }
}
```

</details>

## Model RendezvousRouter

Model dokumentacyjny Java 21. Poniższy blok zawiera pełne typy, importy, komentarze i mechanizm przykładu. Nie
jest osobnym plikiem źródłowym ani uruchamianą aplikacją; ograniczenia i przebieg opisano w tym dokumencie.

<details>
<summary>Rozwiń pełny przykład: RendezvousRouter</summary>

```java
package pl.jakubtworek.patterns.labs.scaling;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/**
 * Highest Random Weight / Rendezvous Hashing: deterministyczny właściciel klucza.
 */
public final class RendezvousRouter {
    private final List<String> nodes;

    public RendezvousRouter(List<String> nodes) {
        this.nodes = nodes.stream().sorted().toList();
        if (nodes.isEmpty()
                || nodes.stream().anyMatch(String::isBlank)
                || nodes.stream().distinct().count() != nodes.size()) {
            throw new IllegalArgumentException("Unique nonblank node names required");
        }
    }

    public String owner(String key) {
        Objects.requireNonNull(key);
        String winner = null;
        long bestScore = 0;
        for (String node : nodes) {
            long score = score(key, node);
            if (winner == null || Long.compareUnsigned(score, bestScore) > 0) {
                winner = node;
                bestScore = score;
            }
        }
        return winner;
    }

    private static long score(String key, String node) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            // Prefiks długości usuwa niejednoznaczność konkatenacji ("ab", "c") i ("a", "bc").
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(keyBytes.length).array());
            digest.update(keyBytes);
            digest.update(node.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest.digest()).getLong();
        } catch (NoSuchAlgorithmException impossibleOnJdk) {
            throw new IllegalStateException("JDK must provide SHA-256", impossibleOnJdk);
        }
    }
}
```

</details>

## Scenariusze poprawności

Scenariusze poprawności w notacji JUnit. Pokazują dane, działanie i oczekiwany stan. Są materiałem
dokumentacyjnym; moduł nie ma zależności JUnit ani automatycznego uruchamiania tych asercji.

<details>
<summary>Rozwiń pełny przykład: DistributedDataLabTest</summary>

```java
package pl.jakubtworek.patterns.labs;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.jakubtworek.patterns.labs.consistency.FencedResource;
import pl.jakubtworek.patterns.labs.consistency.GCounter;
import pl.jakubtworek.patterns.labs.scaling.RendezvousRouter;

class DistributedDataLabTest {
    @Test
    void counterMergesConcurrentReplicaIncrementsWithoutCountingReplayTwice() {
        var a = GCounter.empty().increment("A", 3);
        var b = GCounter.empty().increment("B", 2);
        var merged = a.merge(b);
        assertEquals(5, merged.value());
        assertEquals(merged, merged.merge(a).merge(b));
        assertEquals(3, a.value());
    }

    @Test
    void mergeIsCommutativeAssociativeAndIdempotentForIndependentExamples() {
        for (int x = 1; x <= 5; x++) {
            for (int y = 1; y <= 5; y++) {
                var a = GCounter.empty().increment("A", x);
                var b = GCounter.empty().increment("B", y);
                var c = a.increment("C", 4);
                assertEquals(a.merge(b), b.merge(a));
                assertEquals(a.merge(b).merge(c), a.merge(b.merge(c)));
                assertEquals(a, a.merge(a));
            }
        }
    }

    @Test
    void sameReplicaIdCannotRepresentTwoIndependentWritersWithoutLostIncrements() {
        var firstWriter = GCounter.empty().increment("A", 3);
        var secondWriter = GCounter.empty().increment("A", 2);
        // Kontrprzykład kontraktu: replika musi być jedynym właścicielem swojej składowej.
        assertEquals(3, firstWriter.merge(secondWriter).value());
        assertNotEquals(5, firstWriter.merge(secondWriter).value());
    }

    @Test
    void fencedStoreRejectsPausedOwnerAfterNewOwnershipWasClaimed() {
        var resource = new FencedResource();
        resource.claim(7);
        resource.write(7, "A before pause");
        resource.claim(8);
        resource.write(8, "B after takeover");
        assertThrows(IllegalStateException.class, () -> resource.write(7, "A resumed too late"));
        assertEquals("B after takeover", resource.read());
    }

    @Test
    void fencingTokenMustBeClaimedAndStrictlyIncrease() {
        var resource = new FencedResource();
        assertThrows(IllegalStateException.class, () -> resource.write(1, "unclaimed"));
        resource.claim(2);
        assertThrows(IllegalStateException.class, () -> resource.claim(2));
        assertThrows(IllegalStateException.class, () -> resource.claim(1));
        assertThrows(IllegalStateException.class, () -> resource.write(3, "also unclaimed"));
    }

    @Test
    void rendezvousOwnerDoesNotDependOnNodeListOrder() {
        var first = new RendezvousRouter(List.of("A", "B", "C"));
        var reordered = new RendezvousRouter(List.of("C", "A", "B"));
        for (int key = 0; key < 100; key++) {
            assertEquals(first.owner("key-" + key), reordered.owner("key-" + key));
        }
    }

    @Test
    void addingNodeOnlyMovesKeysWhoseNewWinnerIsAddedNode() {
        var before = new RendezvousRouter(List.of("A", "B", "C"));
        var after = new RendezvousRouter(List.of("A", "B", "C", "D"));
        int moved = 0;
        for (int key = 0; key < 1000; key++) {
            String original = before.owner("key-" + key);
            String changed = after.owner("key-" + key);
            if (!original.equals(changed)) {
                assertEquals("D", changed);
                moved++;
            }
        }
        assertTrue(moved > 0 && moved < 1000);
    }

    @Test
    void removingNodeKeepsOwnersOfAllOtherKeys() {
        var before = new RendezvousRouter(List.of("A", "B", "C"));
        var after = new RendezvousRouter(List.of("A", "C"));
        for (int key = 0; key < 100; key++) {
            String original = before.owner("key-" + key);
            if (!original.equals("B")) {
                assertEquals(original, after.owner("key-" + key));
            }
        }
    }
}
```

</details>
