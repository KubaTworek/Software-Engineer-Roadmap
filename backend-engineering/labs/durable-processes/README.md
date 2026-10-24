# Trwały import i akceptacja zamówienia

Samodzielny build Java 21 izoluje Spring Batch 5.2.4 od Spring Boot 4 głównego
modułu. To celowa granica edukacyjna: nie zmieniamy stacku pozostałych modułów.
Temporal SDK i temporal-testing mają wspólną wersję 1.39.0.

Z `backend-engineering` uruchom:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -f labs/durable-processes/pom.xml verify
```

`RestartableImportJob` jest rzeczywistą definicją Spring Batch; test inicjalizuje
JobRepository w H2 i dowodzi rollbacku porcji oraz wznowienia przez nowy reader.
Parametr `-Dimport.rows=1000000 -Dtest=RestartableImportTest` uruchamia większy
zbiór. Nie jest to benchmark pamięci ani dowód streamingu sterownika PostgreSQL;
przy nim fetch size i transakcję odczytu trzeba skonfigurować osobno.

`ApprovalWorkflowImpl` wykonuje Signal → trwałe oczekiwanie → charge → fulfill.
Retry po utracie odpowiedzi działa ze stabilnym kluczem, a awaria fulfillment
wykonuje kompensację. Test z in-memory ledgerem wyjaśnia granicę idempotencji,
nie zastępuje trwałego providera. Temporal test server umożliwia replay i time
skipping bez Dockera. Historia jest pobierana z testowego wykonania i odtwarzana
przez WorkflowReplayer; nie utrwalamy danych klientów w repozytorium.

`getVersion` zachowuje ścieżkę DEFAULT_VERSION dla historii sprzed dodania
fulfillmentu. Test replay bieżącej historii nie dowodzi zgodności ze wszystkimi
historycznymi wersjami; przed produkcyjnym rolloutem dodaj zanonimizowane historie
każdej wspieranej wersji. Testy nie uruchamiają HA Temporal Service.

Źródła: [Spring Batch](https://docs.spring.io/spring-batch/reference/),
[Temporal Java testing](https://docs.temporal.io/develop/java/best-practices/testing-suite),
[Temporal versioning](https://docs.temporal.io/develop/java/workflows/versioning).
Test wyłącza cache Workflow i wstrzymuje polling poprzedniego workera, następnie tworzy nową WorkerFactory i sprawdza
wznowienie WAITING → Signal → PAID z historii. Serwer testowy pozostaje żywy;
to nie test restartu OS ani trwałości dysku klastra. Osobny test awarii refundu
wymaga jawnego COMPENSATION_FAILED zamiast pozornego COMPENSATED.
## Anulowanie i restart procesu

`CancellationCompensationTest` anuluje workflow po charge, gdy fulfillment
wysyła heartbeat. WAIT_CANCELLATION_COMPLETED czeka na zakończenie Activity,
a refund działa w detached cancellation scope. Test wymaga refundu przed
zamknięciem jako Canceled. Nieudany refund nadal kończy się COMPENSATION_FAILED.
Termination nie wykonuje tego cleanupu. Activity ignorująca anulowanie może
wykonać późny efekt; provider potrzebuje idempotencji, refundu/tombstone i
rekoncyliacji. SDK nie zastępuje tej gwarancji zewnętrznego systemu.
Mechanizm odpowiada [przykładowi SDK Temporal](https://github.com/temporalio/samples-java/blob/main/core/src/main/java/io/temporal/samples/hello/HelloDetachedCancellationScope.java).

`ForkedBatchRestartTest` kończy pierwszą JVM po FAILED na rekordzie 251,
uruchamia drugą JVM z tym samym plikowym H2 i parametrami JobInstance,
a następnie sprawdza 1000 rekordów, sumę ID i dwa wykonania jednego JobInstance.
Nie jest to test kill -9: po takim zabiciu może pozostać STARTED, wymagający
potwierdzenia śmierci poprzedniego workera i kontrolowanej naprawy metadata.
Nie wolno resetować STARTED, gdy stary worker nadal wykonuje efekty.
