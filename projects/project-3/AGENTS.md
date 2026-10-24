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

Przy tematach bazodanowych odróżniaj politykę wyboru odczytu od wdrożonego
routera replik. Brak pomiaru lag nie jest zerem; read-your-writes i retencja
WAL wymagają jawnego runbooka. Test modelu nie potwierdza failoveru ani RPO.

Laboratorium opisuj jako: problem → niezmiennik → naiwny wariant → rozwiązanie → test awarii
→ sygnał produkcyjny → ograniczenie. Nie przedstawiaj planowanej funkcji jako zaimplementowanej.

Tryb app.security.enabled wymaga walidacji JWT w każdej usłudze i ownership
z sub. Nagłówek/body klienta nie jest tożsamością. Worker ma service credential,
nie utrwalony token klienta. Endurance i FinOps odróżniają fizyczny request,
logiczny checkout i unikalne PAID; brak mianownika to UNKNOWN, a ceny estymacji
muszą być jawne. Nie zapisuj surowych raportów ani tokenów w Git.
