# AGENTS.md — Project 2

## Cel

`project-2` jest projektem portfolio integrującym materiał ze Stage 2 modułu
`backend-engineering`. To **modularny monolit marketplace**, a nie zbiór niezależnych
snippetów. Nowy temat powinien być pokazany w przepływie zamówienia albo w małym,
jednoznacznie opisanym laboratorium na jego granicy.

## Zakres Stage 2

- Stage 2A: DDD, bounded contexts, dependency rule, Clean Architecture, REST,
  GraphQL, gRPC, ewolucja kontraktów, modularny monolit i saga;
- Stage 2B: Kafka, outbox, idempotencja, retry/DLQ, WebSocket, CDC,
  rekoncyliacja, replay i backfill;
- Stage 2C: konfiguracja runtime, kontener, Kubernetes, progressive delivery,
  rollback, game day i incident response;
- Stage 2D: threat modeling, walidacja granic, SSRF/upload, sekrety,
  bezpieczne logi i supply chain.

## Reguły zmian

Stage 2E–2F dodają trwały staging feedu katalogu, opcjonalną rekoncyliację
UNKNOWN przez Temporal i modernizację granicy odczytu. Nie uruchamiaj dwóch
koordynatorów sagi zamówienia. Import należy do Catalog; checkpoint i efekt
porcji wymagają jednej transakcji. Shadow nie może powtarzać komend biznesowych.

- Najpierw nazwij niezmiennik lub gwarancję, dopiero potem technologię.
- Domena nie zależy od Springa, JPA, HTTP ani adapterów.
- Między bounded contextami preferuj opublikowany kontrakt integracyjny; nie
  współdziel modelu trwałości ani nie czytaj tabel należących do innego modułu.
- Zapis agregatu i outboxa jest jedną lokalną transakcją. Efekt konsumenta i marker
  idempotencji także są jedną lokalną transakcją; offset jest commitowany później.
- Zakładaj at-least-once, zmianę kolejności, częściową awarię i wiele replik.
- Komentarze wyjaśniają decyzję Stage 2, granicę gwarancji i kontrprzykład. Nie
  komentuj składni Javy ani oczywistych getterów.
- Każde laboratorium dokumentuj schematem: problem → niezmiennik → naiwne
  rozwiązanie → poprawne rozwiązanie → test dowodzący → granica produkcyjna.
- Aktualizuj README/ADR/kontrakt, jeśli zmieniasz publiczne API, zdarzenia, granice
  transakcji, konfigurację lub sposób wdrożenia.

## Weryfikacja

```powershell
.\gradlew.bat test
.\gradlew.bat integrationTest
.\gradlew.bat clean check
```

Test jednostkowy ma dowodzić jednej gwarancji. Test integracyjny służy do kontraktu
z PostgreSQL, Kafką, Springiem lub HTTP, którego fake nie potrafi potwierdzić.

Kontakt Notification jest jedynym właścicielem emaila/nazwy w nowym flow.
Erase ma trwały registry, projekcję i outbox. Rejestr suppression po restore
musi pochodzić spoza odtwarzanego backupu i mieć zaufany manifest; nie utożsamiaj
usunięcia kontaktu z usunięciem finansowych referencji całego systemu.
