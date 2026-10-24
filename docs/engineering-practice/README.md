# Prowadzenie zmiany technicznej — warsztat na projektach

To ćwiczenie dokumentacyjne etapu 4. Nie wymaga tworzenia kolejnej aplikacji.
Artefakty mają oddzielać założenia od wyników testów oraz prawdziwe review od
samodzielnej symulacji.

## RFC do wykonania: czy przenieść sagę marketplace do Temporal?

**Problem:** UNKNOWN payment może czekać dłużej niż request; potrzebujemy
restartu i diagnozy procesu bez ponownego obciążenia. Obecny DB process manager
już utrwala sagę i outbox. Brak danych o koszcie on-call oznacza niewiadomą,
nie argument za migracją.

**Opcje:** zachować scheduler+DB; użyć Temporal tylko do rekoncyliacji; przenieść
całą sagę z kontrolowanym cutoverem. Rekomendacja obecnej implementacji to druga
opcja jako opt-in. Rozdziela eksperyment od krytycznej ścieżki zamówienia.

**Niezmienniki:** jeden koordynator procesu; brak drugiego obciążenia; UNKNOWN
nie staje się REJECTED wskutek samego timeoutu; zdarzenie biznesowe po lokalnym
commicie; historia bez sekretów providera.

**Plan:** test SDK → adapter istniejącego use case → opt-in worker → replay
historii → obserwacja jednej grupy zamówień → decyzja na podstawie wyników.
Przed migracją aktywnych sag potrzebne są mapowanie stanów, watermark, fencing
starego koordynatora i jawna granica rollbacku. Nie wykonano takiej migracji.

## Review i praca zespołowa

Przedstaw drugiej osobie diagram, opcje i niewiadome. Zanotuj jej rzeczywiste
uwagi, odpowiedź oraz zmianę decyzji. Przy pracy samodzielnej zatytułuj notatkę
„symulacja review”; nie przypisuj fikcyjnych wypowiedzi zespołowi.
Pytania: kto jest właścicielem płatności, jak wykryć dwa workflow-id, co po
niedostępności Temporal, ile kosztuje utrzymanie dodatkowej infrastruktury?

## Trzy zadania domenowe

| Projekt | Mała zmiana | Dowód | Ryzyko do omówienia |
| --- | --- | --- | --- |
| Booking | dopasuj Xmx i rezerwę natywną do limitu repliki | budżet, JFR i pomiar cgroup | RSS/cache i CPU quota nie są samym heapem |
| Marketplace | wznów import i wyjaśnij UNKNOWN płatność | test PostgreSQL oraz historia Workflow | staging nie publikuje ceny; retry nie daje exactly-once |
| Ticketing | ogranicz stale reads podczas lag | polityka odczytu i drill replikacji | utrata primary przy read-your-writes wymaga odmowy |

## Szablon raportu z game day

Wypełnij: hipoteza, środowisko/wersje, zakres wpływu, warunek przerwania,
timeline UTC, obserwowany objaw, decyzja, recovery, integralność danych,
czas odzyskania, ograniczenia dowodu. Dla każdego działania po ćwiczeniu podaj
właściciela, termin i test skuteczności. Postmortem nie służy szukaniu winnych.

Wyniku planowanego eksperymentu nie wpisuj jako wyniku zmierzonego. ADR zapisuje
podjętą decyzję i konsekwencje; RFC zbiera argumenty przed decyzją.

[Mapa implementacji i wykonanej weryfikacji](implementation-map.md).

[Samodzielne zadania przekrojowe](capstones.md) łączą kontrakt, awarię, pomiar,
RFC i ocenę granic dowodu w każdym z trzech projektów.
