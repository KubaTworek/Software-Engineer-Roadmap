# Testy MVP — Etap implementacji 1

Ten zestaw testów ma domknąć pierwszy etap projektu: zwykły monolit Spring Boot z podstawowym flow rezerwacji.

## Co jest testowane

### Testy serwisów

- utworzenie eventu razem z pulą miejsc,
- pobranie eventu po ID,
- obsługa braku eventu,
- utworzenie rezerwacji w statusie `PENDING`,
- zmniejszenie dostępnej pojemności po rezerwacji,
- potwierdzenie rezerwacji,
- anulowanie rezerwacji oczekującej,
- zwolnienie miejsca po anulowaniu rezerwacji oczekującej,
- brak podwójnego zwolnienia miejsca przy drugim anulowaniu,
- blokada oversellingu w sekwencyjnym flow MVP,
- brak możliwości anulowania rezerwacji potwierdzonej,
- brak możliwości potwierdzenia rezerwacji anulowanej.

### Testy HTTP API

- pełny flow HTTP: create event → create reservation → get event → confirm reservation → get reservation,
- walidacja requestu tworzenia eventu,
- odpowiedź `404 NOT_FOUND` dla brakującej rezerwacji,
- odpowiedź `409 CAPACITY_UNAVAILABLE` przy braku miejsc.

### Testy jednostkowe encji

- początkowy status rezerwacji,
- przejścia statusów `PENDING → CONFIRMED`,
- przejścia statusów `PENDING → CANCELLED`,
- idempotencja anulowania,
- blokada niepoprawnych przejść statusów.

## Jak uruchomić

```bash
mvn test
```

Testy używają profilu `test` oraz bazy H2 w trybie zgodności z PostgreSQL.

## Ważna uwaga

To nadal nie jest Etap 2 — Concurrency. Test `preventsOversellingInSequentialMvpFlow` sprawdza tylko sekwencyjne zachowanie MVP. W Etapie 2 trzeba dodać osobne testy równoległe z `ExecutorService`, `CountDownLatch` i celową reprodukcją race condition.
