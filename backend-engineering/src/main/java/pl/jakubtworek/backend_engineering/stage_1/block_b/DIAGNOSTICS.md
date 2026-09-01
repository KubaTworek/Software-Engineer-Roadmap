# Scenariusze JFR i GC

Ten runbook służy do stawiania i sprawdzania hipotez. Nie zawiera oczekiwanych
„dobrych” czasów ani stałych progów, ponieważ wynik zależy od CPU, systemu operacyjnego,
wersji JDK, limitów kontenera i obciążenia maszyny.

Przed eksperymentem skompiluj klasy z katalogu `backend-engineering`:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -DskipTests compile
New-Item -ItemType Directory -Force target\jfr, target\gc | Out-Null
```

## CPU-bound kontra wait-bound

```powershell
java '-XX:StartFlightRecording=filename=target/jfr/cpu-vs-io.jfr,settings=profile,dumponexit=true' `
  -cp target/classes `
  pl.jakubtworek.backend_engineering.stage_1.block_b.cpu_vs_io.ProfilingCaseStudyApp `
  --durationSeconds=30 --cpuIterations=50000 --simulatedIoMillis=50 `
  --mixedCpuIterations=10000 --mixedWaitMillis=20

jfr summary target\jfr\cpu-vs-io.jfr
jfr print --events jdk.ExecutionSample,jdk.ThreadPark target\jfr\cpu-vs-io.jfr
```

Pytania diagnostyczne:

- Które metody dominują w `ExecutionSample`, a które tylko w czasie ściennym?
- Czy wątki wait-bound są `TIMED_WAITING`/`PARKED`, mimo że request trwa długo?
- Czy wzrost liczby wątków zwiększył throughput, czy tylko kolejkę i przełączenia?
- Jaka obserwacja odróżnia brak CPU od oczekiwania na zależność?

## Allocation rate

```powershell
java '-XX:StartFlightRecording=filename=target/jfr/allocation.jfr,settings=profile,dumponexit=true' `
  -cp target/classes `
  pl.jakubtworek.backend_engineering.stage_1.block_b.allocation_rate.AllocationProfileRunner

jfr summary target\jfr\allocation.jfr
jfr print --events jdk.ObjectAllocationInNewTLAB,jdk.ObjectAllocationOutsideTLAB `
  target\jfr\allocation.jfr
```

Pytania diagnostyczne:

- Które typy i stosy odpowiadają za największą liczbę bajtów, a nie tylko obiektów?
- Czy alokacja jest wynikiem kontraktu biznesowego, czy zbędnej materializacji?
- Czy spadek allocation rate zmienił czas GC albo latency, czy jedynie mikrobenchmark?
- Czy optymalizacja zachowała ten sam wynik i zakres danych?

## Lock contention

```powershell
java '-XX:StartFlightRecording=filename=target/jfr/locks.jfr,settings=profile,dumponexit=true' `
  -cp target/classes `
  pl.jakubtworek.backend_engineering.stage_1.block_b.lock_contention.LockContentionCaseStudy `
  --threads=8 --durationSecondsPerScenario=15

jfr summary target\jfr\locks.jfr
jfr print --events jdk.JavaMonitorEnter,jdk.ThreadPark target\jfr\locks.jfr
```

Pytania diagnostyczne:

- Który monitor lub parking point kumuluje najwięcej czasu oczekiwania?
- Czy `LongAdder` pomaga przy wielu zapisach, ale komplikuje odczyt dokładnej sumy?
- Czy porównanie używa tej samej liczby workerów i tego samego czasu scenariusza?
- Czy wyższy throughput nie ukrywa nieakceptowalnej niesprawiedliwości wątków?

## G1 kontra ZGC

Oba uruchomienia muszą mieć ten sam heap i identyczne argumenty workloadu:

```powershell
java -Xms256m -Xmx256m -XX:+UseG1GC `
  '-Xlog:gc*,safepoint:file=target/gc/g1.log:time,uptime,level,tags' `
  -cp target/classes `
  pl.jakubtworek.backend_engineering.stage_1.block_b.g1_vs_zgc.GcCaseStudyApp `
  --durationSeconds=30 --allocationBatchSize=5000 --objectSizeBytes=512 `
  --liveSetTargetMb=96 --mediumLivedRetentionCycles=10 --latencyProbeSleepMillis=10

java -Xms256m -Xmx256m -XX:+UseZGC `
  '-Xlog:gc*,safepoint:file=target/gc/zgc.log:time,uptime,level,tags' `
  -cp target/classes `
  pl.jakubtworek.backend_engineering.stage_1.block_b.g1_vs_zgc.GcCaseStudyApp `
  --durationSeconds=30 --allocationBatchSize=5000 --objectSizeBytes=512 `
  --liveSetTargetMb=96 --mediumLivedRetentionCycles=10 --latencyProbeSleepMillis=10
```

Pytania diagnostyczne:

- Czy workload osiągnął porównywalny allocation rate i live set w obu procesach?
- Jak wyglądają rozkład i maksimum pauz, a nie tylko średnia?
- Ile CPU i cykli kolektora kosztuje osiągnięty profil latency?
- Czy heap po GC stabilizuje się, czy live set stale rośnie?
- Czy różnica pochodzi z kolektora, czy z innego przebiegu workloadu?

## Zapis eksperymentu

Do wyniku dołącz:

- commit, wersję JDK i pełną komendę,
- CPU, RAM, system operacyjny i limity kontenera,
- hipotezę postawioną przed uruchomieniem,
- surowy artefakt JFR/log GC oraz obserwowane metryki,
- wniosek ograniczony do badanego workloadu,
- następny eksperyment, który może obalić ten wniosek.

Pliki `.jfr`, logi i wyniki JMH są artefaktami lokalnymi. Nie należy zapisywać ich
w repozytorium jako uniwersalnych wartości referencyjnych.
