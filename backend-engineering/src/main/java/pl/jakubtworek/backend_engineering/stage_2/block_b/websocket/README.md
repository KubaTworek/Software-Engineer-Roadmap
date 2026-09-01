# WebSocket — reconnect, replay i wolny konsument

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `praktyka-produkcyjna`
> - **Uczy:** WebSocket — reconnect, replay i wolny konsument.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „WebSocket — reconnect, replay i wolny konsument” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=WebSocketRuntimeTest,WebSocketSessionProtocolTest" test`
> - **Role klas:** `NaiveUnboundedSession` = `naive`; `BoundedSessionBuffer` = `correct`; `RoadmapWebSocketConfiguration` = `production-boundary`.
> - **Granica:** Laboratorium pokazuje kontrakt i failure modes; nie zastępuje pełnego testu end-to-end ani operacyjnej konfiguracji środowiska.
<!-- material-card:end -->

## Problem i niezmiennik

Długie połączenie może zerwać się po wysłaniu zdarzenia, ale przed jego
zastosowaniem lub potwierdzeniem. Klient po reconnect nie może dostać cichej
luki, a wolny odbiorca nie może powodować nieograniczonego wzrostu pamięci.

## Kontrprzykład i rozwiązanie

`NaiveUnboundedSession` buforuje wszystko bez limitu. `BoundedSessionBuffer`
zwraca jawną decyzję `SLOW_CONSUMER` i usuwa tylko potwierdzone sekwencje.
`ResumableEventLog` nadaje monotoniczny numer per kanał, przechowuje ograniczone
okno i odrzuca resume, którego nie potrafi już obsłużyć bez utraty danych.
`RoadmapEventWebSocketHandler` wystawia ten protokół przez prawdziwy endpoint
Spring WebSocket, a test runtime wykonuje handshake, `RESUME` i replay.
`ClientEventCursor` deduplikuje sekwencje i wykrywa lukę, a
`HeartbeatDeadline` używa czasu monotonicznego do wykrycia half-open session.

## Najważniejszy test

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=WebSocketSessionProtocolTest,WebSocketRuntimeTest" test
```

Test pokrywa replay po `lastSeenSequence`, przekroczenie retencji, backpressure
wolnego klienta i pamięciową konsekwencję naiwnego bufora.

## Kiedy użyć, a kiedy nie

WebSocket ma sens dla dwukierunkowych sesji o małym opóźnieniu. SSE jest prostsze
dla strumienia serwer→klient, a polling dla rzadkich aktualizacji. Sam WebSocket
nie zapewnia trwałości, kolejności globalnej ani exactly-once.

## Granice produkcyjne

Test potwierdza realny handshake i wymianę ramek. Produkcja musi jeszcze dodać
auth, bezpieczne odświeżenie uprawnień, heartbeat, half-open detection, limity
frame, broker/backplane i stateless routing. `lastSeenSequence` wymaga trwałego
logu; pamięć jednej instancji nie wystarcza po restarcie lub przełączeniu węzła.
