# Etap 4 — Spring pod maską

Ten etap nie dodaje nowych funkcji biznesowych. To kontrolowany zestaw pułapek pokazujących, gdzie Spring przestaje działać tak, jak początkujący developer zwykle zakłada.

Projekt nadal jest zwykłym monolitem warstwowym. Pakiet `service.pitfall` jest edukacyjnym obszarem eksperymentalnym, a nie docelowym stylem implementacji funkcji biznesowych.

## 1. Self-invocation i `@Transactional`

Klasa: `SelfInvocationPitfallService`

Przypadek błędny:

```java
public boolean callTransactionalMethodThroughThis() {
    return transactionalMethod();
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public boolean transactionalMethod() {
    return TransactionSynchronizationManager.isActualTransactionActive();
}
```

Wynik: `false`.

Powód: metoda oznaczona `@Transactional` jest wywołana przez `this`, więc wywołanie nie przechodzi przez proxy Springa. Adnotacja jest obecna w kodzie, ale interceptor transakcyjny nie ma szansy się uruchomić.

Przypadek poprawny:

```java
public boolean callTransactionalMethodThroughProxy() {
    return selfProvider.getObject().transactionalMethod();
}
```

Wynik: `true`.

To nie jest rekomendacja, żeby wszędzie wstrzykiwać samego siebie. To demonstracja mechanizmu. W normalnym kodzie lepiej przesunąć granicę transakcji do publicznej metody wołanej z innego beana albo zmienić podział odpowiedzialności.

Endpoint:

```http
GET /api/spring-pitfalls/transactional/self-invocation
```

Test:

```bash
mvn test -Dtest=SpringPitfallStage4IntegrationTest#selfInvocationBypassesTransactionalProxy
```

## 2. `@Transactional` i lazy loading

Klasa: `LazyLoadingPitfallService`

Przypadek błędny:

```java
public Reservation loadDetachedReservationWithLazyRelations(UUID reservationId) {
    return reservationRepository.findById(reservationId).orElseThrow(...);
}
```

Kontroler potem próbuje zmapować encję do DTO i dotyka relacji `reservation.getEvent()` oraz `reservation.getCustomer()` poza aktywnym persistence contextem.

Endpoint błędny:

```http
GET /api/spring-pitfalls/reservations/{reservationId}/lazy-broken
```

Oczekiwany wynik: HTTP 500 z kodem `LAZY_INITIALIZATION`.

### Naprawa 1 — mapowanie wewnątrz transakcji

```java
@Transactional(readOnly = true)
public SpringPitfallReservationView mapInsideTransaction(UUID reservationId) {
    Reservation reservation = reservationRepository.findById(reservationId).orElseThrow(...);
    return toView(reservation);
}
```

Endpoint:

```http
GET /api/spring-pitfalls/reservations/{reservationId}/lazy-fixed-transaction
```

### Naprawa 2 — DTO projection

Repository zwraca od razu DTO, bez ekspozycji encji poza warstwę dostępu do danych.

Endpoint:

```http
GET /api/spring-pitfalls/reservations/{reservationId}/lazy-fixed-projection
```

### Naprawa 3 — fetch join

Repository pobiera encję razem z wymaganymi relacjami.

Endpoint:

```http
GET /api/spring-pitfalls/reservations/{reservationId}/lazy-fixed-fetch-join
```

### Naprawa 4 — entity graph

Repository używa `@EntityGraph(attributePaths = {"event", "customer"})`.

Endpoint:

```http
GET /api/spring-pitfalls/reservations/{reservationId}/lazy-fixed-entity-graph
```

Testy:

```bash
mvn test -Dtest=ApiSpringPitfallStage4IntegrationTest
```

## 3. Spring AOP i proxy boundary

Adnotacja: `@Measured`

Aspekt:

```java
@Around("@annotation(pl.jakubtworek.booking.aop.Measured)")
```

Klasa: `MeasuredPitfallService`

Przypadek błędny:

```java
public String callMeasuredMethodThroughThis(String input) {
    return measuredOperation(input);
}

@Measured("spring-pitfall-measured-operation")
public String measuredOperation(String input) {
    return input.toUpperCase();
}
```

Wynik: operacja się wykonuje, ale aspekt nie zapisuje pomiaru.

Przypadek poprawny:

```java
public String callMeasuredMethodThroughProxy(String input) {
    return selfProvider.getObject().measuredOperation(input);
}
```

Wynik: aspekt zapisuje pomiar w `MeasurementRegistry`.

Endpointy:

```http
POST /api/spring-pitfalls/aop/through-this?input=spring
POST /api/spring-pitfalls/aop/through-proxy?input=spring
```

## 4. Lifecycle beanów i singleton scope

Klasa: `BeanLifecycleProbe`

Pokazuje:

- `@PostConstruct`,
- `InitializingBean.afterPropertiesSet`,
- singleton scope,
- fakt, że `@PreDestroy` nie jest wykonany przed zamknięciem kontekstu.

Endpoint:

```http
GET /api/spring-pitfalls/bean-lifecycle
```

## Najważniejszy wniosek

Spring nie działa przez magiczne skanowanie adnotacji w dowolnym miejscu kodu. Wiele mechanizmów, w tym `@Transactional` i AOP, działa przez proxy. Jeżeli wywołanie nie przechodzi przez proxy, interceptor nie zostanie uruchomiony.

To jest powód, dla którego self-invocation jest jedną z najczęstszych pułapek w aplikacjach Springowych.
