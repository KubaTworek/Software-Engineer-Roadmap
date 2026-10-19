# Notification System — runbook

## Rosnący wiek kolejki

1. Rozdziel backlog według priorytetu, kanału, tenanta i providera.
2. Zatrzymaj kampanie oraz digesty; zachowaj capacity pool dla ruchu krytycznego.
3. Sprawdź limity providera, saturację workerów i wiek outboxa, nie tylko długość kolejki.
4. Skaluj workery tylko wtedy, gdy provider i baza mają pozostałą przepustowość.

## Wynik `UNKNOWN`

Nie wykonuj automatycznego retry ani fallbacku. Odszukaj operację po provider idempotency key,
sprawdź API statusowe lub raport providera, a dopiero potem oznacz próbę jako przyjętą albo bezpieczną do ponowienia.

## Rosnący DLQ

Pogrupuj błędy na permanentne dane odbiorcy, wadliwy template i awarię zależności. Replay wykonuj
po usunięciu przyczyny, zachowując ten sam logiczny klucz dostarczenia. Nigdy nie replayuj całej kolejki bez limitu.

Outbox `DEAD`, job DLQ oraz poison webhook to trzy różne zbiory. Najpierw ustal, na której granicy zatrzymał się
przepływ. Replay outboxa odtwarza materializację joba; replay joba może ponownie dotknąć providera; replay webhooka
tylko aktualizuje lokalną wiedzę. Każdy z nich wymaga innego dowodu, że ponowienie jest bezpieczne.

## Wygasające lease'y i utknięte przetwarzanie

1. Porównaj `lease_until` z czasem bazy, nie zegarem laptopa operatora.
2. Sprawdź, czy czas wywołania providera nie przekracza długości lease'u.
3. Nie zeruj aktywnych lease'ów masowo — dwa workery mogłyby wykonać ten sam efekt.
4. Dla wygasłego lease'u pozostaw ten sam job ID i provider idempotency key.
5. Jeśli poprzedni worker mógł wysłać request, rozpocznij od reconciliation, a nie od fallbacku.

## Poison webhook

Po wyczerpaniu budżetu inbox oznacza callback jako dead i kontynuuje pozostałe eventy. Zachowaj oryginalne raw body,
event ID, błąd i wersję adaptera. Napraw parser/mapowanie, przetestuj na kopii, a następnie wykonaj selektywny replay.
Nie zmieniaj event ID i nie obchodź monotonicznej maszyny stanów ręcznym downgrade'em rekordu.

## Rotacja sekretu webhooka

1. Dodaj nowy sekret jako current, a poprzedni zachowaj chwilowo jako previous.
2. Przełącz konfigurację po stronie providera i obserwuj błędy podpisu.
3. Utrzymuj overlap dłużej niż maksymalne opóźnienie oraz retry callbacków.
4. Usuń previous dopiero, gdy nie ma już callbacków podpisanych starym kluczem.
5. W produkcji pobieraj oba sekrety z KMS/secret managera; nie loguj wartości ani sygnatur.

## Spadek delivery rate

Porównaj `SENT` z callbackami `DELIVERED`, bounce i complaint. Sprawdź opóźnienie webhooków oraz
reputację domeny/nadawcy. `SENT` oznacza akceptację przez providera, a nie dotarcie do człowieka.
