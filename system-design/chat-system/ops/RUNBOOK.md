# Chat System — runbook operacyjny

Ten runbook dotyczy uruchamialnego modularnego monolitu. Nazwy Kafka, Redis, S3
i OpenSearch w głównym README opisują granice architektury docelowej; poniższe
kroki nie zakładają, że te usługi istnieją w lokalnym środowisku.

## Zasady bezpieczeństwa podczas incydentu

- Nie loguj treści wiadomości, JWT, tokenów uploadu ani bajtów załączników.
- Najpierw ogranicz wpływ awarii, potem naprawiaj backlog. Nie usuwaj rekordów
  outboxa ani tombstone'ów, aby „odblokować” system.
- Przed ręczną zmianą danych zapisz identyfikatory zdarzeń i zakres sekwencji.
- Presence, typing i push mogą zostać zdegradowane. Trwały zapis oraz odczyt
  historii mają pierwszeństwo.

## 1. Rosnący lub zatrzymany outbox

Sygnały: wzrost `NEW`/`FAILED`, brak `PUBLISHED`, rosnąca kolejka lokalna albo
alert `ChatOutboxBacklogGrowing`.

1. Sprawdź zdrowie bazy i opóźnienia transakcji.
2. Rozdziel problem claimu od problemu handlera: `ENQUEUED` starsze niż lease
   wskazuje przerwany worker; `FAILED` z rosnącym `attempts` wskazuje konsumenta.
3. Zweryfikuj `last_error` bez kopiowania payloadu do publicznych logów.
4. Jeżeli downstream jest przeciążony, ogranicz producentów lub wyłącz
   niekrytyczny handler. Nie zwiększaj bez końca batcha.
5. Po przywróceniu zależności pozwól backoffowi opróżnić backlog. Obserwuj DB,
   aby recovery nie wywołało wtórnego przeciążenia.

Bezpieczny retry opiera się na `event_id` oraz `outbox_handler_deliveries`.
Ręczne oznaczenie `PUBLISHED` jest utratą zdarzenia i wymaga osobnej decyzji
biznesowej.

## 2. Reconnect storm WebSocket

Sygnały: skok liczby handshake, disconnectów, żądań sync, CPU lub puli połączeń.

1. Potwierdź, czy źródłem jest rollout, awaria sieci czy restart gatewaya.
2. Ogranicz handshake per użytkownik/IP i wymagaj jittered exponential backoff
   po stronie klientów.
3. Chroń bazę limitem współbieżnych synców i maksymalnym `limit` strony.
4. Zachowaj możliwość REST sync nawet kosztem wyłączenia typing/presence.
5. Po zdarzeniu sprawdź, czy cursor żadnego urządzenia nie cofnął się oraz czy
   tombstone'y dotarły do klientów długo offline.

## 3. Fałszywe ONLINE lub masowe OFFLINE

1. Sprawdź opóźnienie cleanera i liczbę aktywnych `presence_sessions`.
2. Porównaj TTL lease'u z częstotliwością `/app/presence.heartbeat`.
3. Nie używaj ręcznej zmiany presence do naprawy autoryzacji — presence jest
   tylko sygnałem UX.
4. Przy awarii store zaakceptuj stan `unknown/offline`; nie blokuj wiadomości.

## 4. Niespójność attachment metadata ↔ storage

Sygnały: rekord `READY`, lecz odczyt zwraca brak pliku, albo osierocony obiekt.

1. Zablokuj pobieranie podejrzanego attachmentu (`BLOCKED`).
2. Potwierdź checksumę i status skanu; nie udostępniaj pliku bez ponownej
   walidacji.
3. Odtwórz obiekt z backupu albo pozostaw tombstone w wiadomości.
4. Osierocone pliki usuwaj dopiero po grace period i ponownym sprawdzeniu
   referencji w bazie.

## 5. Konflikty sekwencji lub brak wiadomości w historii

1. Zatrzymaj zapis tylko dla dotkniętej konwersacji, jeżeli to możliwe.
2. Sprawdź unikalność `(conversation_id, sequence_number)`, `next_sequence`
   konwersacji i eventy outbox związane z zakresem.
3. Nie renumeruj opublikowanej historii — cursory urządzeń odwołują się do tych
   wartości. Brakującą treść napraw nową, audytowalną operacją.
4. Uruchom celowany test PostgreSQL przed przywróceniem ruchu:

```powershell
.\mvnw.cmd -Pinfrastructure-tests -Dit.test=PostgreSqlChatInfrastructureIT verify
```

## Backup i restore

Skrypty znajdują się w `ops/backup`. Restore uważa się za sprawdzony dopiero,
gdy można zalogować się, odczytać historię po sekwencji, potwierdzić membership,
odnaleźć rekordy outboxa i zweryfikować referencje attachmentów. Sam poprawny
exit code `pg_restore` nie dowodzi spełnienia RPO/RTO.
