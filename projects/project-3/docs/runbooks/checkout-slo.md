# Checkout SLO: diagnoza po zużyciu error budget

Populacja: poprawne, uwierzytelnione (gdy secure) POST /orders. Walidacja DTO,
ownership oraz błędne zasoby/klucze zwracające IllegalArgumentException są wyłączone.
Good: lokalny COMMIT zakończony i PAID w <=3 s. PAYMENT_UNKNOWN, timeout, 5xx i
późny PAID są bad. Późniejsza praca workera nie zmienia historycznego doświadczenia.

app_checkout_requests_total ma tylko outcome good/bad. app_checkout_duration_seconds
ma histogram z granicą 3 s. Cel demonstracyjny: 99.9% w 30 dniach, burn 14.4 dla
1h+5m lub burn 6 dla 6h+30m. Brak ruchu daje NaN/unknown, nie 100% dostępności.
Brak scrape wymaga oddzielnego alertu platformy. Mały ruch wymaga osobnej polityki,
nie podbijania sztucznie mianownika. Odczytaj [SRE Workbook](https://sre.google/workbook/alerting-on-slos/).

1. Sprawdź, czy licznik pochodzi ze wszystkich replik i czy nie jest scrape'owany
   podwójnie. Lokalna konfiguracja historycznie miała host i container target.
2. Powiąż pierwsze bad z wdrożeniem i trace. Sprawdź oczekiwanie na połączenie DB,
   bulkhead i czasy payment/reservation. Ustal, czy to wynik niepewny czy odrzucenie.
3. Sprawdź wiek backlogu i poison outbox, nie tylko liczność kolejki.
4. Ogranicz napływ lub wyłącz wadliwą zmianę. Nie zwiększaj retry ani liczby replik
   bez sprawdzenia capacity zależności.
5. Po naprawie wymagaj spadku krótkiego burn rate, postępu workerów i zgodności
   order/payment/reservation/inbox. Długie okno nadal może zawierać incydent.

promtool test rules weryfikuje reguły na syntetycznej historii, także brak alertu
przy celu i zerowym ruchu. Nie uruchamia człowieka on-call ani nie dowodzi 30 dni
SLO. Endurance ma osobny, logiczny mianownik checkoutów; retry HTTP w serwerowym
SLI są fizycznymi próbami. Nie porównuj tych mianowników jako identycznych.
