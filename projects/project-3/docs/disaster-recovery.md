# Disaster recovery

## Wymagania

| Klasa danych | RPO | RTO | Mechanizm | Dowód |
| --- | --- | --- | --- | --- |
| zamówienia, rezerwacje, inbox/outbox | 5 min | 60 min | RDS PITR + kopia cross-region | kwartalny restore do izolowanego środowiska |
| katalog | 30 min | 2 h | snapshot + odbudowa cache | restore i test API katalogu |
| Redis cache/rate limit | brak gwarancji danych | 15 min | odtworzenie klastra; cache jest odbudowywalny | chaos `redis-down.js` |
| RabbitMQ | 5 min | 60 min | durable queues, backup definicji, replay outboxa | test redelivery i replay |

HA ogranicza awarie w jednym regionie; DR odtwarza usługę po utracie regionu lub danych.
Multi-AZ bez testu restore nie spełnia wymagań DR.

## Restore drill

1. Zanotuj czas rozpoczęcia i najnowszy dostępny punkt przywracania.
2. Odtwórz snapshot/PITR do odizolowanej sieci i nigdy nie kieruj tam ruchu produkcyjnego.
3. Uruchom migracje tylko w trybie zgodnym z przywróconą wersją aplikacji.
4. Sprawdź niezmienniki inventory, zamówienia bez terminalnego stanu i backlog outboxa.
5. Wykonaj smoke test odczytu, następnie kontrolowany replay outboxa.
6. Zapisz osiągnięte RPO/RTO; przekroczenie celu tworzy działanie naprawcze.

Rollback migracji preferuje forward-fix oraz expand/contract. Destrukcyjna migracja nie może
wejść, dopóki poprzednia wersja aplikacji może zostać przywrócona podczas rollout.
