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

## Co sprawdza lokalny drill

Uruchom apps i smoke-test w prywatnym COMPOSE_PROJECT_NAME=roadmap-..., następnie
scripts/local-restore-drill.ps1. Skrypt zatrzymuje Order Service na czas snapshotu,
porównuje count i hash kanonicznej zawartości orders/outbox z odtworzeniem,
uruchamia osobny Order Service na losowym porcie loopback z przywróconą bazą
i wyłączonym workerem/relayem, odczytuje PAID i ponawia zamówienie przez HTTP.
Sprawdza te same ID/status/kwotę, rezerwację, płatność i marker inboxa dla
potwierdzonego outboxa; retry nie może zmienić snapshotu. W finally usuwa wyłącznie
własny kontener, bazę o losowej nazwie i dump oraz uruchamia oryginalną usługę.

To quiesced snapshot jednej bazy i odczyt/idempotentny retry terminalnej sagi.
Nie dowodzi wspólnego PITR kilku baz, replay zaległych efektów po utracie regionu,
nowej płatności na przywróconym środowisku ani produkcyjnego RPO/RTO.
Manifest używa string_agg i MD5 jako kontroli przypadkowej zmiany małego labu,
nie jako zabezpieczenia autentyczności backupu ani narzędzia dla TB danych.

operational-drill.ps1 utrzymuje ruch podczas ograniczonej awarii Redis,
RabbitMQ i order-db, a po recovery wymaga PAID, stabilnego retry, zgodności
payment/reservation i zbieżności outbox/inbox. Wynik obejmuje krótki lokalny
scenariusz; nie jest pomiarem SLO, testem failoveru klastra ani długim soak testem.
