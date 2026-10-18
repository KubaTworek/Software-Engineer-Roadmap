# AGENTS.md — Project 3

## Cel

Project 3 jest przekrojowym projektem Stage 3: pokazuje nie tylko podział na mikroserwisy,
ale przede wszystkim zachowanie systemu ticketingowego pod współbieżnością, przeciążeniem
i częściową awarią. Każda deklarowana gwarancja powinna mieć kod, test lub wykonywalny
eksperyment oraz sygnał pozwalający rozpoznać jej naruszenie na produkcji.

## Centralne gwarancje

- Reservation Service jest właścicielem inventory i nigdy nie pozwala zejść poniżej zera.
- Retry płatności nie może wykonać podwójnego obciążenia.
- DB oraz broker nie tworzą wspólnej transakcji; Order Service używa outboxa, a konsument inboxa.
- Częściowy sukces jest jawnym stanem wymagającym rekoncyliacji, a nie ogólnym `FAILED`.
- Lease nie jest gwarancją poprawności bez fencing tokenu sprawdzanego przez zasób.
- Timeout pojedynczej próby nie może przekraczać pozostałego deadline całego requestu.
- Przeciążenie jest ograniczane przez bulkhead i load shedding, nie przez nieograniczoną kolejkę.
- SLO, RPO i RTO są mierzalnymi wymaganiami, a nie nazwami usług AWS.

## Komentarze edukacyjne

Komentuj dokładnie mechanizmy Stage 3: granice spójności, failure windows, idempotencję,
at-least-once delivery, fencing, backpressure, retry budget, SLO, recovery i kompromisy cloud.
Nie komentuj składni Javy, getterów ani oczywistych delegacji. Komentarz powinien mówić,
jaką gwarancję chroni kod, przed jakim kontrprzykładem i czego nadal nie gwarantuje.

## Testy

- Zwykłe `./mvnw verify` pozostaje szybkie i nie wymaga Dockera.
- Testy z prawdziwym PostgreSQL/Redis/RabbitMQ oznaczaj `@Tag("infrastructure")` i uruchamiaj
  profilem `-Pinfrastructure-tests`.
- Test rozproszony powinien badać historię oraz niezmiennik, nie tylko status pojedynczego HTTP.
- Nie zastępuj PostgreSQL przez H2 w testach locków, izolacji i atomowego SQL.

## Dokumentacja

Laboratorium opisuj jako: problem → niezmiennik → naiwny wariant → rozwiązanie → test awarii
→ sygnał produkcyjny → ograniczenie. Nie przedstawiaj planowanej funkcji jako zaimplementowanej.

