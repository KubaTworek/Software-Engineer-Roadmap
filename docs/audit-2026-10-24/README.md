# Audyt merytoryczny — backend-engineering i projekty 1–3

Data: 2026-10-24. Stan: lokalny master `125ceeb` wraz z niezacommitowanymi
rozszerzeniami roadmapy. Audyt obejmuje istniejące materiały i ostatnio dodany kod.
Nie zmieniano implementacji w ramach audytu; dodano raport i izolowane reprodukcje.

**Raport historyczny.** Poprawki i aktualne dowody opisuje
[weryfikacja z 25 października](../remediation-2026-10-25.md).
Źródła w `probes/` zachowują asercje wykrytych błędów i nie są testami regresji.
Runner kieruje obecnie do właściwych testów poprawnego zachowania.

## Wniosek

Zakres tematyczny jest szeroki. Największe luki dotyczą spójności efektów
zewnętrznych z lokalną transakcją, bezpieczeństwa rotacji tokenów, współbieżnych
przejść stanu oraz dowodów operacyjnych. Zielony build nie wykrywa wszystkich
tych okien awarii. Poprzednią ocenę kompletności rozszerzeń trzeba zawęzić:
część nowych materiałów to działający rdzeń lub model edukacyjny, a nie pełny
przepływ biznesowy i wdrożeniowy.

Przegląd uwzględnił mapy etapów, buildy, profile CI, dokumentację laboratoriów,
główne przepływy domenowe i testy ich granic. Nie stanowi formalnego dowodu
poprawności każdej linii ani przeglądu bezpieczeństwa infrastruktury produkcyjnej.
Wyniki statyczne i odtworzone eksperymentalnie są oznaczone osobno.

## Najważniejsze ustalenia

| ID | Priorytet | Obszar | Ustalenie | Dowód |
| --- | --- | --- | --- | --- |
| A1 | P1 | Projekt 3 | Zewnętrzna płatność i confirm poprzedzają trwały commit Order | realny stack, odrzucony commit: orders=0, payments=1, reservation=CONFIRMED |
| A2 | P1 | Backend 1C | Wyjątek reuse cofa unieważnienie rodziny refresh tokenów | rzeczywiste proxy Spring/JPA: następca pozostaje aktywny |
| A3 | P1 | Projekt 2 | Równoległe rekoncyliacje UNKNOWN nie serializują zmiany | realny PostgreSQL: dwa PaymentReserved dla jednej logicznej zmiany |
| A4 | P1 | Backend 2E | Anulowanie po charge nie uruchamia skutecznego refundu | Temporal SDK: charge=true, refunds=0 po anulowaniu |
| A5 | P1 | Projekt 1 | Rotacja refresh tokenu nie chroni read-modify-write między transakcjami | analiza repozytorium i encji; brak Lock, CAS i Version |
| A6 | P2 | Projekt 1 | Błąd cache po commicie może zmienić udany zapis w błąd HTTP | analiza afterCommit i adaptera Redis; brak testu awarii w tym oknie |
| A7 | P2 | Projekt 2 | Shadow odczyt blokuje starą ścieżkę bez własnego deadline | analiza synchronicznego candidate.apply pod monitorem |
| A8 | P2 | Backend 2A | Model ochrony GraphQL nie jest podłączony do endpointu runtime | analiza wywołań guard/resolver; testy modelu i HTTP są oddzielne |
| A9 | P2 | Backend 2E / projekt 2 | Import nie ma pełnego lifecycle i publikacji kompletnego feedu | API append/cancel, schema bez COMPLETE i brak ścieżki publikacji |
| A10 | P2 | Projekt 2 | JWT secure nie definiuje wymaganego issuer i audience | analiza własnego JwtDecoder i testów negatywnych |
| A11 | P2 | Projekt 3 | Restore/recovery sprawdza zbyt mało, by dowieść DR i SLO | porównanie skryptów z wymaganiami disaster-recovery |
| A12 | P3 | Dokumentacja | Pozostały sprzeczne opisy i niepełna nawigacja buildów | pięć zamiast sześciu suite'ów; komentarze o nieistniejącej mapie/wewnętrznej invalidacji |

P1 oznacza błąd gwarancji istotnej dla danych lub bezpieczeństwa. P2 oznacza
istotną lukę dydaktyczną, testową lub operacyjną. Świadomie opisane ograniczenie
modelu nie jest samo w sobie błędem; znaczenie ma zakres deklarowanej kompletności.

## A1 — commit lokalny po efektach zewnętrznych

Kod: [OrderService](../../projects/project-3/order-service/src/main/java/pl/jakubtworek/backend/order/application/OrderService.java),
linie 108–109, 178–208 i 229–255; [OrderEntity](../../projects/project-3/order-service/src/main/java/pl/jakubtworek/backend/order/domain/OrderEntity.java), fabryka pending.

`create()` obejmuje jedną transakcją zapis PENDING, wywołanie płatności,
potwierdzenie rezerwacji i outbox. `repository.save()` nie zatwierdza nadrzędnej
transakcji. Komentarz o trwałym zapisie próby przed payment jest więc nieprawdziwy.
Awaria przy końcowym commicie może usunąć całą lokalną historię, gdy efekty
w innych usługach są już zatwierdzone.

**Odtworzenie:** na osobnym stacku Compose zadziałał odroczony constraint trigger,
który odrzucił commit wyłącznie zamówienia użytkownika audytowego. Wynik:
`orders=0`, `payments=1`, `reservation=CONFIRMED`. Retry z tym samym HTTP
Idempotency-Key nie odzyskał zamówienia: nie było rekordu klucza, a rezerwacja
nie była już PENDING. To deterministyczny test awarii commitu, nie symulacja
zewnętrznego efektu mockiem w jednostkowym teście.

**Uzupełnienie:** zatwierdzona intencja przed I/O, stabilna tożsamość procesu,
oddzielne transakcje przejść i worker rekoncyliacji. Dodać test odmowy commitu
oraz restartu po każdym efekcie. Wydłużanie transakcji lub samo saveAndFlush
nie zamyka tego okna. Reprodukcja: [order-commit-failure.ps1](probes/order-commit-failure.ps1).

## A2 — reuse refresh tokenu nie unieważnia rodziny trwale

Kod: [RefreshTokenService](../../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/authorization/RefreshTokenService.java),
linie 63–74; [wyjątek](../../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/authorization/RefreshTokenReuseException.java).

Metoda @Transactional zmienia rodzinę tokenów, a następnie rzuca RuntimeException.
Domyślny rollback cofa te zmiany. Test jednostkowy bada obiekty zmodyfikowane
w pamięci, więc nie widzi, że transakcja nie zatwierdziła unieważnienia.

**Odtworzenie:** Spring Boot + rzeczywiste repozytorium JPA, bez transakcji testowej
obejmującej całe wywołanie. Po odrzuceniu starego tokenu nowy odczyt z DB nadal
zwrócił jednego aktywnego następcę. [Probe](probes/RefreshFamilyAuditProbeTest.java).

**Uzupełnienie:** zaprojektować osobną granicę trwałego unieważnienia albo zwracać
wynik decyzji z zatwierdzanej transakcji i mapować go na wyjątek poza nią.
Dodać test przez proxy, z odczytem po zakończonej transakcji. Sam mock repozytorium
nie może dowodzić gwarancji revocation. Dotyczy też odmowy EXPIRED, jeśli ma
zapisywać trwały powód unieważnienia.

## A3 — dwa lookupi UNKNOWN generują dwa nowe fakty

Kod: [ReconcileUnknownPaymentUseCase](../../projects/project-2/src/main/java/pl/jakubtworek/marketplace/payment/application/ReconcileUnknownPaymentUseCase.java),
linie 21–29; [JdbcPaymentRepository](../../projects/project-2/src/main/java/pl/jakubtworek/marketplace/payment/infrastructure/JdbcPaymentRepository.java).

Dwie transakcje mogą odczytać UNKNOWN przed lookupem, a potem obie zapisać
RESERVED i różne eventId. UPSERT nie sprawdza wersji ani oczekiwanego poprzedniego
stanu. Deduplikacja konsumenta po eventId nie rozpozna dwóch różnych identyfikatorów
tej samej logicznej zmiany.

**Odtworzenie:** dwa wątki i bariera po odczycie UNKNOWN, rzeczywiste transakcje
PostgreSQL oraz outbox. Zapisano dwa PaymentReserved. To nie dowód dwóch obciążeń
karty — ta ścieżka wykonuje lookup — lecz dowód naruszenia pojedynczego przejścia
i niechronionej aktualizacji. [Probe](probes/PaymentRaceAuditProbeTest.java).

**Uzupełnienie:** serializacja/wersja albo warunkowy UPDATE statusu z atomowym
outboxem i ponownym odczytem po konflikcie. Przetestować również wyścig resolve
z release/refund. Zablokowanie wiersza na cały wolny lookup ma koszt dla puli DB;
trzeba go zestawić z rozdzieleniem I/O i warunkowego zapisu.

## A4 — cancellation wymaga osobnej ścieżki kompensacji

Kod: [ApprovalWorkflowImpl](../../backend-engineering/labs/durable-processes/src/main/java/pl/jakubtworek/durable/ApprovalWorkflowImpl.java),
linie 24–39.

Obsłużono błąd Activity, lecz nie zaprojektowano cleanupu poza anulowanym scope.
Zarejestrowanie kompensacji samo jej nie wykonuje. Po skutecznym charge i cancel
podczas fulfillmentu refund nie został wykonany.

**Odtworzenie:** rzeczywisty TestWorkflowEnvironment i anulowanie przez WorkflowStub.
Wynik: `charge=true`, `refunds=0`. [Probe](probes/CancellationAuditProbeTest.java).

**Uzupełnienie:** cleanup w odpowiednim niezależnym CancellationScope, świadomy
ActivityCancellationType, idempotentny refund i jawny stan ręcznej naprawy.
Osobno rozróżnić cancel i terminate, ponieważ terminate nie uruchamia cleanupu.
Zobacz [dokumentację Temporal](https://docs.temporal.io/develop/java/workflows/cancellation).

Brakuje również wykonywalnych przykładów heartbeat długiej Activity, Update
oraz zgodności ze starą historią. Bieżący replay własnej historii jest użyteczny,
ale nie dowodzi kompatybilności historycznych wersji. README częściowo już to
uczciwie zaznacza; nadal są to elementy do pogłębienia zakresu roadmapy.

## A5 — wyścig rotacji refresh tokenu w Booking

Kod: [AuthService.refresh](../../projects/project-1/src/main/java/pl/jakubtworek/booking/service/security/AuthService.java),
linie 156–183; [repozytorium](../../projects/project-1/src/main/java/pl/jakubtworek/booking/repository/RefreshTokenRepository.java)
i [encja](../../projects/project-1/src/main/java/pl/jakubtworek/booking/entity/RefreshToken.java).

Odczyt aktywności i revoke nie mają blokady, CAS ani @Version. Dwa równoległe
requesty mogą przejść sprawdzenie przed wzajemnym commitem i wydać dwóch następców.
To wniosek z analizy kodu, nie odtworzony w tym audycie test konkurencyjnej rotacji.

**Uzupełnienie:** test na PostgreSQL z dwoma niezależnymi transakcjami; dokładnie
jedna rotacja akceptowana, zdefiniowana reakcja na reuse i ewentualna revocation
rodziny. Clock powinien być jawny również na tej ścieżce, jak w rezerwacjach.

## A6 — afterCommit nie izoluje awarii cache

Kod: [ReservationService](../../projects/project-1/src/main/java/pl/jakubtworek/booking/service/ReservationService.java),
linie 349–363; [adapter Redis](../../projects/project-1/src/main/java/pl/jakubtworek/booking/cache/RedisAvailabilitySnapshotCache.java).

Eviction po commicie jest poprawniejszą kolejnością niż publikacja stanu przed
commitem, ale wyjątek Redis może wyjść z callbacku już po trwałej rezerwacji.
Klient dostanie błąd mimo zapisu, a retry create nie ma klucza idempotencji.
Nie zmienia to ochrony SQL przed oversellingiem; dotyczy niejednoznacznego wyniku
operacji biznesowej i zależności dostępności API od optymalizacji cache.

**Uzupełnienie:** test wyłączenia cache dokładnie po commicie, określona odpowiedź
API, strategia ponownej invalidacji/TTL oraz idempotencja komendy, jeśli jest częścią
kontraktu. Nie wystarczy ukryć wyjątku bez oceny nieświeżych odczytów.

## A7 — shadow comparison bez budżetu czasu

Kod: [ShadowCatalogReader](../../projects/project-2/src/main/java/pl/jakubtworek/marketplace/catalog/infrastructure/ShadowCatalogReader.java),
linie 21–27 i 36–40.

`candidate.apply()` wykonuje się synchronicznie pod monitorem. Wyjątek jest
obsłużony, ale wolny lub zawieszony kandydat zatrzymuje starą odpowiedź i kolejne
wywołania tego adaptera. Ponadto próbka jest liczona przez całe życie instancji,
bez okna/epoki wersji; po zmianie kandydata nie ma jasnego resetu dowodów promocji.

**Uzupełnienie:** timeout i izolowana, ograniczona concurrency shadow,
pomijanie porównania przy przeciążeniu oraz próbka związana z wersją i oknem czasu.
Testować zawieszenie kandydata, nie tylko natychmiastowy wyjątek.

## A8 — GraphQL jest obecny, lecz dowody nie łączą wszystkich granic

Kod: [ProductGraphQlController](../../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/graphql/ProductGraphQlController.java)
i [QueryComplexityGuard](../../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/graphql/QueryComplexityGuard.java).

Kompendium ma rzeczywisty endpoint HTTP i oddzielne modele kosztu, batchingu oraz
autoryzacji pola. Guard nie jest wywoływany na ścieżce runtime resolvera.
Obecność testu modelu i osobnego testu HTTP nie dowodzi wspólnego zachowania
„odrzuć kosztowne query przed odczytem danych”.

Projekt 2 ma guard AST, ale mnożnik `first` rozpoznaje tylko literal IntValue;
zmienne, wartości domyślne i schema-aware limity wymagają rozwinięcia, gdy dojdą
listy. Obecna schema jest mała, więc to luka dydaktyczna, nie odtworzony exploit
istniejącego endpointu listowego.

**Uzupełnienie:** jeden test endpointu z aliasami/fragmentami/variables, licznikiem
odczytów, odmową pola i DataLoader per request. Pokazać także null propagation
oraz różnicę błędu transportu, walidacji schema i błędu resolvera.

## A9 — batch i import kończą się na rdzeniu mechanizmu

Kod: [RestartableImportJob](../../backend-engineering/labs/durable-processes/src/main/java/pl/jakubtworek/durable/RestartableImportJob.java),
[SupplierCatalogImport](../../projects/project-2/src/main/java/pl/jakubtworek/marketplace/catalog/infrastructure/SupplierCatalogImport.java)
i [migracja V6](../../projects/project-2/src/main/resources/db/migration/V6__supplier_import.sql).

Mamy wartościowy test chunk rollback/restart i staging z checkpointem PostgreSQL.
Nie mamy jednak pełnego run lifecycle: COMPLETE/FAILED, potwierdzenia EOF i liczby
rekordów, publikacji kompletnego feedu do katalogu, raportu/quarantine błędów ani
wykonywalnego import/export od wejścia do końca. Nie sprawdzono restartu JVM
Batch na trwałym JobRepository. Milion rekordów H2 nie dowodzi ograniczonej
pamięci sterownika PostgreSQL; cursor nie ma tu jawnego fetch size i modelu
transakcji strumieniowego odczytu. Opisy granic są częściowo już poprawne.

**Uzupełnienie:** jedno pełne zadanie feedu dostawcy, nie kolejny framework:
niezmienne wejście → checkpoint → walidacja/raport → finalizacja → publikacja
bez ujawnienia częściowego feedu. Dodać restart procesu, anulowanie w połowie
porcji i weryfikację integralności po wznowieniu.

## A10 — niepełny kontrakt akceptowanego JWT

Kod: [SecurityConfiguration](../../projects/project-2/src/main/java/pl/jakubtworek/marketplace/security/SecurityConfiguration.java),
linie 42–44.

Własny decoder z kluczem HMAC nie określa wymaganych iss/aud. Testy wystawiają
tokeny bez tych pól. To akceptacja poprawnie podpisanego tokenu bez dowodu, że
wystawiono go dla tego API i uzgodnionego wystawcy. Nie jest to zarzut wobec
jawnie lokalnego profilu permitAll; dotyczy kontraktu profilu secure.

**Uzupełnienie:** wymagany issuer/audience, negatywne testy obu, wygasanie i rotacja
kluczy. Poprawny przykład validatorów istnieje już w kompendium 1C.
Mechanizm opisuje [Spring Security](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html#_configuring_a_custom_validator).

## A11 — zakres pomiaru restore/recovery jest zbyt mały

Kod: [local-restore-drill.ps1](../../projects/project-3/scripts/local-restore-drill.ps1),
linie 9–16; [operational-drill.ps1](../../projects/project-3/scripts/operational-drill.ps1),
linie 20–22; [wymagania DR](../../projects/project-3/docs/disaster-recovery.md).

Restore porównuje liczbę orders. Równa liczba nie dowodzi tej samej zawartości,
spójności outbox/inbox ani relacji payment–reservation–order. Bieżący COUNT(*)
źródła może też zmienić się po snapshot przy aktywnym ruchu.
Operational drill natychmiast startuje zatrzymaną zależność i czeka na health;
nie sprawdza zachowania requestów w trakcie awarii, retry amplification, narastania
backlogu i późniejszej rekoncyliacji. Wyniki tych skryptów są użyteczne, lecz mają
węższy zakres niż recovery krytycznej ścieżki i osiągnięcie RPO/RTO całego systemu.

**Uzupełnienie:** manifest snapshotu z identyfikatorami/checksum, niezmienniki
między usługami, outbox replay i journey po restore. Do chaos dodać kontrolowany
ruch przed/w trakcie/po, czas bez zależności, kryteria przerwania oraz dowód braku
utraconych i podwójnych efektów. Polityka ReplicaReadPolicy nadal pozostaje modelem,
a nie wdrożonym routingiem do read replica — README uczciwie to zaznacza.

## A12 — zgodność komentarzy i indeksów

- [README kompendium](../../backend-engineering/README.md) nadal opisuje pięć suite'ów
  infrastrukturalnych zamiast sześciu i nie dodaje Batch/Temporal do tabeli buildów.
- [ReservationService](../../projects/project-1/src/main/java/pl/jakubtworek/booking/service/ReservationService.java),
  okolice linii 217: komentarz twierdzi, że invalidacja jest wewnątrz transakcji,
  chociaż helper rejestruje afterCommit.
- [PaymentController](../../projects/project-3/payment-mock-service/src/main/java/pl/jakubtworek/backend/payment/api/PaymentController.java),
  okolice linii 37: komentarz opisuje mapę w pamięci, chociaż istnieje trwały ledger.
- Adaptery ShadowCatalogReader i ReplicaReadPolicy oraz staging feedu trzeba
  przedstawiać jako konkretne fragmenty pokrycia nowego zakresu, nie dowód
  kompletności wdrożenia wszystkich rozszerzeń roadmapy.

## Co jest już mocne

- Rozdzielenie szybkich testów od kontraktów prawdziwych silników.
- Atomowe inventory, locking, fencing i marker konsumenta + efekt lokalny.
- Rzeczywiste CDC PostgreSQL → Debezium → Kafka, a nie tylko mapa w pamięci.
- Rozróżnienie timeout/UNKNOWN od znanej porażki oraz istnienie procesów rekoncyliacji.
- Testy kontrprzykładów, property/model-based, mutation i jasne granice wielu modeli.
- Osobne buildy i zachowanie różnego celu etapów oraz trzech projektów.

Nie ma potrzeby ponownie dodawać tematów typu CDC, backpressure, virtual threads,
gRPC czy GraphQL: już istnieją. Największą wartość da mocniejsze powiązanie
opisu mechanizmu z zachowaniem runtime, transakcją oraz rzeczywistą awarią.

## Wykonana weryfikacja

| Obszar | Komenda / wynik |
| --- | --- |
| Backend — infrastruktura | `mvnw.cmd -Pinfrastructure-tests verify` — 19 testów, bez błędów i pominięć |
| Backend — aplikacja delivery 2C | osobny `pom.xml verify` — 12 testów, sukces |
| Backend — aplikacja cloud 3C | osobny `pom.xml verify` — 44 testy, sukces; powtórzono poza sandboxem z powodu attach agenta Mockito |
| Projekt 1 — infrastruktura | `mvnw.cmd -Pinfrastructure-tests verify` — sukces, w tym 7 testów Failsafe |
| Projekt 1 — benchmark | `mvnw.cmd -Pjmh-smoke -Dtest=JmhSmokeTest test` — sukces; nie jest to miarodajny benchmark produkcyjny |
| Projekt 2 — CDC | `gradlew.bat cdcIntegrationTest --no-daemon` — sukces |
| Projekt 3 — infrastruktura | `mvnw.cmd -Pinfrastructure-tests verify` — cały reactor 8 modułów, sukces |
| Projekt 3 — pełny system | osobny stack Compose, build obrazów Java 21, `smoke-test.ps1` — PASS, także restart Payment Mock |
| Reprodukcje A1–A4 | potwierdzone oddzielnymi probeami; ich zielony wynik oznacza odtworzenie znanej luki, nie poprawność systemu |

Końcowe mvnw.cmd verify kompendium zakończyło się sukcesem: 469 testów, zero
błędów, jeden pominięty. Końcowe gradlew.bat test integrationTest check assemble
--no-daemon projektu 2 również zakończyło się sukcesem. Reprodukcje pozostają
poza standardowymi suite'ami; tymczasowe źródła i klasy zostały usunięte.
Lokalny Maven/Gradle używał JDK 23 z release/target 21; obrazy systemowego smoke testu budowano i uruchamiano na Java 21. Terraform nie był dostępny w PATH, więc nie wykonano osobnego terraform validate.
Security scan NVD, produkcyjne wdrożenia cloud, HA Temporal i długie testy soak
nie były częścią tej weryfikacji. Repo nie ma jednego wspólnego buildu.

## Reprodukcje

Kod probeów pozostaje w katalogu [probes](probes/run-java-probe.ps1), poza standardową
suite. Runner nie nadpisuje istniejących plików, tymczasowo dodaje nazwany test,
a następnie usuwa własny plik oraz jego wygenerowane klasy. Próby celowo
sprawdzają obecność luki; po jej naprawie powinny przestać przechodzić.

Z katalogu głównego:

```powershell
pwsh -File docs/audit-2026-10-24/probes/run-java-probe.ps1 -Probe RefreshFamily
pwsh -File docs/audit-2026-10-24/probes/run-java-probe.ps1 -Probe PaymentRace
pwsh -File docs/audit-2026-10-24/probes/run-java-probe.ps1 -Probe Temporal
```

Probe awarii commitu wymaga osobnego stacka projektu 3 i zmiennej
COMPOSE_PROJECT_NAME zaczynającej się od `roadmap-audit-ticketing-`.
Nie uruchamiaj go na współdzielonym środowisku. Tymczasowy trigger ogranicza się
do użytkownika audytu i jest usuwany w finally. Stack użyty podczas tego audytu
został utworzony pod odrębną nazwą, a następnie usunięty ze wszystkimi profilami, siecią i własnymi danymi testowymi.

## Kolejność uzupełnień

1. Zamknąć A1–A5 testami regresji sprawdzającymi oczekiwaną poprawną gwarancję.
2. Doprecyzować kontrakty cache, JWT i shadow oraz przetestować negatywne ścieżki.
3. Domknąć jeden pełny import i migrację read modelu w projekcie 2.
4. Rozszerzyć DR i chaos o integralność oraz journey pod ruchem.
5. Uzupełnić testy endpointów GraphQL i historycznej kompatybilności Temporal.
6. Wyrównać komentarze, indeksy buildów i tabelę dowodów z faktycznym zakresem.
