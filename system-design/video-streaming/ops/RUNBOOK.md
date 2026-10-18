# Video Streaming — runbook

Alert jest początkiem diagnozy, a nie dowodem przyczyny. Najpierw potwierdź
wpływ na użytkownika: możliwość rozpoczęcia playbacku, dostępność manifestu i
segmentów oraz liczbę dotkniętych materiałów lub transmisji.

## TranscodingDeadLetters

1. Odczytaj `jobId`, `videoId`, `attempt` i błąd z rekordu joba oraz wiadomości DLQ.
2. Sprawdź dostępność source object i zgodność deklarowanego rozmiaru.
3. Odtwórz komendę FFmpeg na kontrolowanej kopii wejścia; nie publikuj manifestu ręcznie.
4. Po usunięciu przyczyny utwórz nową próbę z nowym fencing tokenem. Consumer jest
   idempotentny, ale ponowne przetwarzanie nadal może nadpisać roboczy prefix.

## TranscodingFailureRatioHigh

Rozdziel awarie wejścia (uszkodzony codec), zasobów (CPU/dysk), storage i brokera.
Porównaj failure ratio z czasem transkodowania oraz retry. Wstrzymaj automatyczny
retry, jeżeli zwiększa saturation; preferuj kontrolowany drain kolejki.

## LivePipelineFailures

Sprawdź ingest RTMP, stderr FFmpeg, wolne miejsce i ownership lease. Jeżeli inna
replika przejęła token, zakończenie starego procesu jest oczekiwanym fencingiem.
Nie obniżaj fencing tokenu i nie ustawiaj statusu `LIVE` ręcznie.

## QoeIngestionFailures

Potwierdź, czy awaria dotyczy tylko telemetrii. Playback nie powinien zależeć od
zapisu QoE. Sprawdź walidację event type, RabbitMQ i PostgreSQL, a następnie
odtwórz bezpiecznie wyłącznie zdarzenia telemetryczne — bez efektów biznesowych.

## Bezpieczne zakończenie incydentu

- potwierdź z próbnego klienta master manifest oraz co najmniej dwa warianty;
- sprawdź brak nowych DLQ przez pełne okno alertu;
- udokumentuj timeline, zakres wpływu i przyczynę;
- dodaj test odtwarzający awarię przed zamknięciem postmortem.
