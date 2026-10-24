# Linux i zasoby procesu Java

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** Linux i zasoby procesu Java.
> - **Typowy błąd:** Uznanie wolnego heapu lub samego exit code 137 za diagnozę pamięci kontenera.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=ProcessResourceSnapshotTest" test`
> - **Katalog uruchomienia:** `backend-engineering` (katalog głównego modułu).
> - **Role klas:** brak jawnej roli w nazwach klas; intencję przykładu określają opis i test, nie sam brak prefiksu.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## Problem i niezmiennik

Wolny heap nie gwarantuje, że proces zmieści się w kontenerze. RSS obejmuje
rezydentną pamięć procesu, a `memory.current` dotyczy całej cgroup, w tym
rozliczanego cache. Nie sumuj ich jako niezależnych składników. Budżet JVM musi
zostawić miejsce na stosy, metaspace, kod JIT, direct buffers i inne alokacje.

## Kontrprzykład i rozwiązanie

`ResourcePressureProbe` celowo zatrzymuje direct buffers przy małym `Xmx`.
Dotyka stron pamięci, bo sama rezerwacja przestrzeni adresowej nie dowodzi RSS.
To eksperyment awarii, nie kod aplikacji. `ProcessResourceSnapshot` odczytuje
cgroup v2 i odróżnia `max` od limitu liczbowego. Brak pliku powoduje błąd pomiaru,
a nie raport zdrowego zera. Spadek licznika throttlingu przerywa porównanie.

Kod 137 to SIGKILL. Porównaj `memory.events/oom_kill` przed i po albo sprawdź
`State.OOMKilled`; sam kod nie dowodzi OOM. Java `OutOfMemoryError` ma inną
ścieżkę diagnostyczną. `SIGTERM` daje aplikacji szansę zamknięcia, `SIGKILL` nie
uruchamia hooków. JVM jako PID 1 musi otrzymać sygnał bezpośrednio.

## Uruchomienie

Z `backend-engineering`, Java 21; test parsera działa także na Windows:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=ProcessResourceSnapshotTest" test
pwsh -File scripts/resource-pressure-drill.ps1 -Mode memory
pwsh -File scripts/resource-pressure-drill.ps1 -Mode cpu
pwsh -File scripts/resource-pressure-drill.ps1 -Mode fd
```

Trzy ostatnie polecenia wymagają Docker Engine Linux. Skrypt ogranicza pamięć,
swap, CPU, liczbę procesów i deskryptorów; kompiluje w `/tmp`, montuje źródła
read-only i usuwa wyłącznie swój kontener. Zewnętrzny timeout ogranicza czas próby.
Tryb memory wymaga potwierdzenia OOMKilled, cpu wykrywa v1/v2 i wypisuje `cpu.stat`, fd odtwarza
`Too many open files`. Nie uruchamiaj probe bez izolacji na procesie produkcyjnym.

## Jak czytać wynik

Zestaw `VmRSS` w `/proc/<pid>/status`, `jcmd <pid> GC.heap_info`, NMT po włączeniu
`-XX:NativeMemoryTracking=summary` i `jcmd <pid> VM.native_memory summary`.
NMT nie obejmuje każdej alokacji natywnej. `lsof`/`/proc/<pid>/fd` ujawniają
wycieki deskryptorów; `ss` pomaga odróżnić sockety od zwykłych plików.
`pidstat`, `vmstat` i `iostat` rozdzielają CPU, oczekiwanie i I/O.

Dla CPU porównaj `throttled_usec` i `nr_throttled` w tej samej cgroup w dwóch
chwilach. Podaj bazę procentu CPU: jeden rdzeń, wszystkie rdzenie hosta czy quota.
Tryb cpu pokazuje ograniczenie 0.25 CPU; drugi przebieg bez quota jest osobnym
pomiarem na tej samej maszynie, nie uniwersalnym wynikiem wydajności.

## Test i granice

`ProcessResourceSnapshotTest` używa fixture plików: dowodzi interpretacji licznika,
nie działania kernela. Drill dopiero uruchamia prawdziwy proces na Linuxie.
Ścieżka cgroup zależy od namespace; poza kontenerem odczytaj `/proc/self/cgroup`
i właściwy mount. Brak `cpu.stat` na cgroup bez kontrolera wymaga diagnozy środowiska.
W cgroup v1 	hrottled_time ma jednostkę nanosekund; w v2 	hrottled_usec mikrosekund. Nie porównuj surowych wartości bez konwersji. Parser ProcessResourceSnapshot celowo dotyczy v2; probe CPU i skrypt projektu 1 obsługują obie wersje.
