# Reactive Streams — demand i backpressure

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** Reactive Streams — demand i backpressure.
> - **Typowy błąd:** Emisja ponad demand subskrybenta lub kontynuacja po cancel.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=ReactiveStreamsDemandTest,ReactorRuntimeTest" test`
> - **Katalog uruchomienia:** `backend-engineering` (katalog głównego modułu).
> - **Role klas:** `NaivePushPublisher` = `naive`.
> - **Granica:** Test kontroluje wybrany przeplot w JVM; pojedyncze uruchomienie nie dowodzi wszystkich zachowań dopuszczonych przez JMM ani wydajności pod obciążeniem.
<!-- material-card:end -->

## Problem

Asynchroniczny producent może wytwarzać dane szybciej, niż konsument potrafi je
przetworzyć. Bez kontraktu popytu rośnie bufor, latency i zużycie pamięci, aż
przeciążenie jednego odbiorcy przenosi się na cały proces.

## Niezmiennik

Publisher nie emituje więcej elementów, niż subscriber jawnie zażądał. Po
`cancel`, `onError` lub `onComplete` nie wysyła kolejnych sygnałów.

## Kontrprzykład i poprawne rozwiązanie

`NaivePushPublisher` po pierwszym `request(1)` wysyła całą kolekcję. Test celowo
pokazuje naruszenie kontraktu. `DemandAwarePublisher` utrzymuje demand, stosuje
saturating addition i kończy strumień dokładnie raz.

Laboratorium używa standardowego `java.util.concurrent.Flow`, dzięki czemu
wyjaśnia semantykę bez uzależnienia od Reactor API. WebFlux, Reactor czy RxJava
są adapterami i bibliotekami nad tym samym problemem, a nie jego zamiennikiem.
`ReactiveWorkPipeline` dokłada rzeczywisty Reactor, bounded `flatMap`, wirtualny
czas oraz kontekst requestu niezależny od `ThreadLocal`.

## Najważniejszy test

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=ReactiveStreamsDemandTest,ReactorRuntimeTest" test
```

Test sprawdza limit emisji, anulowanie, niepoprawny demand oraz celowe naruszenie
w naiwnym publisherze.

## Kiedy użyć, a kiedy nie

Reactive Streams daje wartość przy streamingu, nierównych prędkościach etapów i
potrzebie jawnego backpressure. Dla prostego request–response z blocking I/O
virtual threads mogą dać czytelniejszy model. Reactive nie przyspiesza pracy CPU,
nie usuwa limitu połączeń do bazy i nie naprawia nieograniczonego downstreamu.

## Granice produkcyjne

Model `Flow` pozostaje celowo mały, natomiast test Reactor potwierdza demand,
cancellation, virtual time i `Context` w realnym runtime. Nie zastępuje pełnego
Reactive Streams TCK ani testu anulowania konkretnego klienta HTTP/JDBC.
Produkcyjny pipeline nadal musi określić scheduler, timeout, obserwowalność i
politykę błędów.
