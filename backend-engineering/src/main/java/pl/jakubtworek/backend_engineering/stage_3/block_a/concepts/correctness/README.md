# correctness

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `temat-zaawansowany`
> - **Uczy:** correctness.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „correctness” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=DistributedCorrectnessTest,LinearizableRegisterCheckerTest" test`
> - **Role klas:** `NaiveCounterStore` = `naive`; `IdempotentCounterStore` = `correct`; `DeterministicRetrySimulation` = `simulation`.
> - **Granica:** Model weryfikuje nazwany niezmiennik; nie implementuje produkcyjnego protokołu rozproszonego ani infrastruktury dostawcy.
<!-- material-card:end -->

## Testowanie poprawności systemu rozproszonego



## Problem

Test sprawdzający wyłącznie końcową odpowiedź pojedynczego requestu nie widzi
przeplotów, niejednoznacznych timeoutów ani efektów wykonanych przed utratą
odpowiedzi. W systemie rozproszonym trzeba analizować **historię operacji**:
wywołania, efekty, timeouty, retry i odpowiedzi wraz z ich kolejnością.

Laboratorium modeluje komendę zwiększającą licznik. Serwer może zatwierdzić efekt,
po czym klient traci odpowiedź i ponawia komendę. Obie próby mogą wyglądać na
poprawne, chociaż jeden zamiar biznesowy został wykonany dwukrotnie.

## Niezmienniki

| Rodzaj | Pytanie | Niezmiennik w laboratorium |
| --- | --- | --- |
| safety | czy nigdy nie powstał niedozwolony stan? | wartość licznika jest sumą unikalnych logicznych komend |
| liveness | czy system ostatecznie robi postęp? | każda komenda kończy się w ograniczonym budżecie kroków |
| liniowalność | czy operacje można ułożyć jak atomowe, zachowując porządek czasu rzeczywistego? | każdy odczyt rejestru widzi wartość zgodną z pewną legalną sekwencją |

Safety i liveness nie są zamienne. System może odpowiadać szybko i zakończyć
wszystkie requesty, a jednocześnie podwójnie naliczyć płatność. Może też zachować
poprawny stan, lecz nigdy nie zakończyć operacji przez nieskończone retry.

## Naiwny przykład

`NaiveCounterStore` wykonuje każdy fizyczny request. Jeżeli pierwsza próba zapisze
efekt i utraci odpowiedź, retry ponownie zwiększy licznik. Raport może więc mieć
`allRequestsSucceeded=true` i `livenessPreserved=true`, ale
`safetyPreserved=false`.

To odpowiednik systemu, który mierzy tylko statusy HTTP i nie sprawdza historii
efektów. Sam timeout nie mówi, czy operacja się nie wykonała.

## Poprawne rozwiązanie

`IdempotentCounterStore` atomowo zapisuje identyfikator komendy razem z efektem.
Retry używa tego samego `commandId`; druga próba zostaje rozpoznana jako duplikat.
W produkcji marker i zmiana biznesowa muszą należeć do tej samej transakcji lub do
mechanizmu o równoważnej gwarancji.

`DeterministicScheduler` oraz `ControlledClock` usuwają zależność testu od zegara
ściennego i systemowego planisty. Seed ustala kolejność zadań zaplanowanych na ten
sam moment, a trace pozwala dokładnie odtworzyć porażkę. `FailurePlan.random`
generuje powtarzalny zbiór timeoutów po zapisie.

`LinearizableRegisterChecker` pokazuje najmniejszy użyteczny model liniowalności.
Szuka legalnego porządku operacji rejestru, respektując relację: jeśli operacja A
zakończyła się przed rozpoczęciem B, A musi wystąpić wcześniej. Operacje nakładające
się w czasie mogą zostać uporządkowane na różne sposoby.

## Testy

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=DistributedCorrectnessTest,LinearizableRegisterCheckerTest" test
```

Testy demonstrują:

1. kontrprzykład, w którym wszystkie requesty kończą się sukcesem, ale retry łamie
   niezmiennik,
2. zachowanie niezmiennika przez idempotentny zapis przy tych samych awariach,
3. deterministyczne odtwarzanie losowych awarii i przeplotów przez seed,
4. osobną porażkę liveness po wyczerpaniu budżetu kroków,
5. historię liniowalną mimo nakładających się operacji,
6. historię niemożliwą do wyjaśnienia legalną kolejnością rejestru.

## Ograniczenia produkcyjne

- Scheduler jest modelem jednowątkowym; nie odtwarza JVM, sieci ani prawdziwego
  konsensusu.
- Checker ma limit 10 operacji, bo wykonuje przeszukiwanie wykładnicze.
- Seed pomaga reprodukować scenariusz, ale nie dowodzi poprawności dla wszystkich
  możliwych przeplotów.
- Test modelu nie zastępuje testów z prawdziwym brokerem, bazą i awariami procesu.
- Historię produkcyjną trzeba budować z jednoznacznych identyfikatorów operacji,
  czasu monotonicznego lub relacji przyczynowych oraz obserwowalnych punktów commit.
- Dla poważnych protokołów potrzebne są model checking/specyfikacja oraz narzędzia
  analizujące historie; ten przykład uczy kontraktu, nie implementuje Jepsena ani
  algorytmu konsensusu.

## Powiązania

- [lease, fencing i leader election](../coordination/README.md),
- [timeout, retry i circuit breaker](../README.md),
- [idempotentny konsument i retry](../../../../stage_2/block_b/consumer/README.md),
- [CDC, replay i rekoncyliacja](../../../../stage_2/block_b/cdc_reconciliation/README.md).
