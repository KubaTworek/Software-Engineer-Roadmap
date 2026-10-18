# Test wydajności całej usługi

Mikrobenchmark JMH odpowiada na pytanie o koszt małego fragmentu kodu. Ten
scenariusz k6 mierzy pełną ścieżkę HTTP → security → transakcja → PostgreSQL i
sprawdza, czy przy przeciążeniu aplikacja odmawia rezerwacji kontrolowanym `409`,
zamiast zwracać `5xx` albo naruszyć granice capacity.

## Przygotowanie

1. Uruchom PostgreSQL i aplikację z profilem `load-test`.
2. Profil tworzy idempotentnie laboratoryjnego managera. Nie aktywuj go poza
   lokalnym, izolowanym środowiskiem pomiarowym.
3. Funkcja `setup()` k6 loguje managera i tworzy nowy event poza mierzonym
   scenariuszem. `EVENT_ID` i `ACCESS_TOKEN` można nadal podać, jeśli chcesz
   użyć wcześniej przygotowanych danych.

```powershell
$env:JWT_SECRET = "local-load-test-secret-with-at-least-32-characters"
docker compose up -d postgres
mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=load-test"

# W drugim terminalu:
$env:BASE_URL = "http://localhost:8080"
$env:TEST_TYPE = "load"
k6 run performance/k6/reservation-load.js
```

Jeżeli nie instalujesz k6 lokalnie, ten sam smoke test można uruchomić w
kontenerze. Z wnętrza kontenera aplikacja Windows/macOS jest dostępna jako
`host.docker.internal`:

```powershell
docker run --rm `
  -v "${PWD}/performance/k6:/scripts" `
  -e BASE_URL=http://host.docker.internal:8080 `
  -e TEST_TYPE=smoke `
  grafana/k6 run /scripts/reservation-load.js
```

Profile `smoke`, `load`, `stress`, `spike` i `soak` używają modelu otwartego
(`arrival-rate`). Generator próbuje utrzymać tempo niezależnie od odpowiedzi,
dzięki czemu rosnące latency nie ukrywa brakujących pomiarów (coordinated
omission). Profil `closed` celowo pokazuje przeciwny model: stała liczba VU
wysyła kolejne żądanie dopiero po zakończeniu poprzedniego.

## Kryteria eksperymentu

- `p95 < 750 ms` i `p99 < 1500 ms` dla tworzenia rezerwacji;
- mniej niż 1% nieoczekiwanych odpowiedzi;
- brak porzuconych iteracji w profilu arrival-rate;
- `availableCapacity` pozostaje w przedziale `0..totalCapacity`;
- `409 CAPACITY_UNAVAILABLE` jest poprawną degradacją po wyczerpaniu miejsc.

Próg należy ustalić przed testem i powiązać z SLO oraz capacity planem. Nie
podnoś progu po zobaczeniu słabego wyniku. Eksperyment przerwij wcześniej, jeśli
pojawiają się `5xx`, baza zbliża się do limitu połączeń, kolejka stale rośnie albo
środowisko testowe zaczyna wpływać na inne systemy. Po teście zestaw p95/p99 z
CPU, GC, liczbą aktywnych połączeń i lock waitami PostgreSQL; sama średnia nie
wyjaśnia saturacji.
