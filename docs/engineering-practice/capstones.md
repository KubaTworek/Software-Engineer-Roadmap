# Samodzielne zadania przekrojowe

Zadania rozwijają istniejące projekty. Nie wymagają nowego frameworka ani
odtwarzania przykładów linia po linii. Rozpocznij od niezmiennika i hipotezy;
po ćwiczeniu porównaj wynik z testem referencyjnym. To nie certyfikat seniority.

## Projekt 1 — zmiana granicy danych

Przyjmij klientów z nazwami spoza ASCII. Ustal znaczenie limitu długości,
normalizacji, unikalności oraz sortowania; pozostaw jednoznaczną politykę emaila.
Sprawdź oba adaptery zapisu, równoległe rezerwacje i rollback. Wymuś awarię cache
po COMMIT i wyjaśnij, co użytkownik zobaczy, a co nadal ochroni inventory.

Artefakty: kontrakt tekstu, kontrprzykład dwóch reprezentacji, test PostgreSQL,
historia współbieżnych operacji i opis kompatybilności istniejących danych.
Pytania review: gdzie zmieniłeś semantykę? Czy stary klient lub cursor nadal
działa? Co wymaga migracji, a co jest wyłącznie nowym zapisem prezentacyjnym?

## Projekt 2 — erase podczas opóźnionego zdarzenia

Utwórz kontakt, zapełnij cache, zatrzymaj publikację i zrób backup. Usuń kontakt,
ponów operację, dostarcz stary update i przywróć backup do osobnej bazy. Otwórz
odczyt dopiero po sprawdzeniu aktualnego registry spoza backupu. Dodaj podpisany
token innego klienta i sprawdź HTTP, zamiast wywoływać politykę wyłącznie przez new.

Artefakty: granica własności danych, diagram transakcji/outbox/inbox, dowód braku
PII w komunikacie, restore manifest, plan retencji i test odmowy nieaktualnego
rejestru. Pytania review: jakie identyfikatory pozostają? Dlaczego? Co się stanie
po ack brokera i rollbacku SQL? Kto potwierdza świeżość eksportu registry?

## Projekt 3 — decyzja o capacity pod SLO i kosztem

Przygotuj baseline i jeden wariant zmiany limitów zasobów/pul. Zachowaj tę samą
mieszankę ruchu i jawny model kosztów. W czasie stałego napływu wyłącz broker,
a następnie zmierz recovery. Niezależnie obróć klucz IdP i sprawdź dostęp klienta
oraz workera. Nie utożsamiaj znanego klucza w cache z natychmiastowym logoutem.

Artefakty: workload, p95/p99, good/bad SLI, czas/backlog recovery, inventory,
unikalne PAID i inbox, próbki CPU/RSS/puli, koszt per ukończone zamówienie oraz
decyzja promote/rollback. Pytania review: czy tańszy wariant odrzuca więcej pracy?
Czy retry zmieniło mianownik? Czy masz wystarczający horyzont i próbkę dla wniosku?

Do każdego zadania przygotuj krótkie RFC i postmortem eksperymentu: założenie,
obserwacja, wyjaśnienie, granica dowodu i następny pomiar. Review z człowiekiem
pozostaje rzeczywistym zadaniem; nie wpisuj fikcyjnej akceptacji do raportu.
