# Etap implementacji 3 — Asynchroniczność

Ten etap dodaje do zwykłego monolitu kontrolowany eksperyment z asynchronicznością. Celem nie jest robienie mikroserwisów ani kolejki komunikatów, tylko zrozumienie, gdzie w monolicie ma sens `ExecutorService`, `ThreadPoolExecutor` i `CompletableFuture`.

## Co zostało dodane

### Osobna pula wątków

Konfiguracja znajduje się w:

```text
src/main/java/pl/jakubtworek/booking/config/AsyncExecutorConfig.java
```

Projekt używa dwóch executorów:

- `ThreadPoolExecutor bookingAsyncExecutor` — do zadań asynchronicznych,
- `ScheduledExecutorService bookingScheduler` — do timeoutów.

Beany mają `destroyMethod = "shutdown"`, więc Spring poprawnie zamyka pule przy zatrzymaniu aplikacji.

## Flow confirm-async

Endpoint:

```http
POST /api/reservations/{reservationId}/confirm-async?paymentScenario=APPROVED
```

Przepływ:

```text
confirm-async
  -> async: validate payment provider
  -> if approved: confirm reservation in transaction
  -> async fire-and-forget:
       - send confirmation email
       - write audit log
       - notify external system
```

Dla testów istnieje też metoda:

```java
confirmAndWaitForSideEffects(reservationId, scenario)
```

Ona czeka na side-effecty, żeby testy były deterministyczne. Produkcyjny endpoint nie powinien czekać na maila, audyt i webhook.

## Scenariusze płatności

`PaymentScenario` obsługuje:

- `APPROVED` — płatność zaakceptowana,
- `DECLINED` — błąd biznesowy, rezerwacja zostaje `PENDING`,
- `SLOW` — provider śpi 5 sekund, ale aplikacja timeoutuje po 2 sekundach,
- `FAILING` — provider rzuca wyjątek techniczny.

Dla `SLOW` i `FAILING` fallback oznacza rezerwację jako:

```text
PAYMENT_TIMEOUT
```

To jest świadoma decyzja edukacyjna: status pokazuje, że nie udało się bezpiecznie potwierdzić płatności. W prawdziwym systemie można by mieć osobny status płatności, a nie mieszać go ze statusem rezerwacji.

## CompletableFuture — gdzie są użyte konkretne mechanizmy

### thenCompose

Użyte w `AsyncReservationService` do przejścia z walidacji płatności do transakcyjnego potwierdzenia rezerwacji.

```text
payment validation -> thenCompose -> confirm reservation
```

To jest poprawniejsze niż `thenApply`, ponieważ druga operacja sama zwraca `CompletableFuture`.

### thenApply

Użyte do przekształcenia wyniku `allOf` w `AsyncConfirmationResult`.

### thenCombine

Użyte do połączenia wyniku maila i notyfikacji zewnętrznej w `DeliverySummary`.

### allOf

Użyte do fan-in po side-effectach:

```text
email + notification + audit -> allOf -> AsyncConfirmationResult
```

### applyToEither / anyOf-style race

`ExternalNotificationService` odpala dwa webhooki i bierze pierwszy zakończony wynik. To pokazuje wzorzec `anyOf` / race, gdzie wystarczy odpowiedź jednego kanału.

### timeout

`CompletableFutureTimeouts.withTimeout(...)` dokłada timeout 2 sekundy do walidacji płatności.

Jeżeli timeout wygra, oryginalna operacja jest anulowana:

```java
original.cancel(true)
```

### fallback

Fallback dla błędu technicznego lub timeoutu płatności:

```text
mark reservation as PAYMENT_TIMEOUT
```

Fallback dla błędu maila:

```text
confirmation remains CONFIRMED
save failed outbound message
```

### propagacja wyjątków

`DECLINED` nie jest timeoutem ani awarią techniczną. To błąd biznesowy, więc jest propagowany jako `BusinessRuleException`, a rezerwacja zostaje `PENDING`.

### anulowanie i InterruptedException

`PaymentProviderClient` uruchamia wolną walidację przez `ExecutorService.submit(...)`. Dzięki temu można anulować `Future` i przerwać `Thread.sleep(...)`.

Test sprawdza, że wolna operacja faktycznie została przerwana.

## Nowe klasy

```text
service/async/
  AsyncReservationService.java
  PaymentProviderClient.java
  PaymentScenario.java
  PaymentValidationResult.java
  CompletableFutureTimeouts.java
  ReservationStatusService.java
  EmailSenderService.java
  AuditLogService.java
  ExternalNotificationService.java
  AsyncConfirmationResult.java
  DeliverySummary.java
  SideEffectResult.java
```

Nowe encje pomocnicze:

```text
entity/AuditLog.java
entity/OutboundMessage.java
```

Nowe repozytoria:

```text
repository/AuditLogRepository.java
repository/OutboundMessageRepository.java
```

## Testy

Główny test:

```text
src/test/java/pl/jakubtworek/booking/integration/AsyncStage3IntegrationTest.java
```

Uruchomienie:

```bash
mvn test -Dtest=AsyncStage3IntegrationTest
```

Testy pokrywają:

- pozytywny flow płatności,
- fan-out / fan-in side-effectów,
- fallback dla awarii maila,
- timeout payment providera,
- fallback dla awarii technicznej payment providera,
- propagację błędu biznesowego `DECLINED`,
- anulowanie wolnej operacji i obsługę `InterruptedException`.

## Ważne ograniczenia

To nadal jest zwykły monolit. Ten etap celowo nie dodaje Kafki, RabbitMQ ani mikroserwisów.

Side-effecty są lokalne i edukacyjne. W prawdziwym systemie wysyłka maila albo webhook powinny zwykle iść przez wzorzec outbox + worker, żeby nie zgubić operacji po crashu procesu.
