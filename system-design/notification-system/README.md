# Notification System — System Design

<!-- reading-guide:start -->
## Ścieżki czytania

| Cel czytania | Punkt wejścia |
| --- | --- |
| Teoria i model problemu | [Fundamenty teoretyczne projektowania Notification System](#fundamenty-teoretyczne-projektowania-notification-system) |
| Implementacja i jej dowody | [Co rzeczywiście uruchamia projekt](#co-rzeczywiście-uruchamia-projekt) |
| Uruchomienie i eksperymenty | [Uruchamianie](#uruchamianie) |
| Ograniczenia i scenariusze awarii | [Gwarancje dostarczenia i okna awarii](#gwarancje-dostarczenia-i-okna-awarii) |

Wspólne mechanizmy i ich testy znajdziesz w [indeksie pojęć backend-engineering](../../backend-engineering/README.md#globalny-indeks-pojęć). Tutaj śledź ich konsekwencje dla opisywanego systemu.
Diagramy docelowe i warianty rozwoju czytaj razem z mapą implementacji; sam opis architektury nie oznacza, że wszystkie jej komponenty są uruchamiane w laboratorium.
<!-- reading-guide:end -->

## Zacznij tutaj

| Pytanie | Odpowiedź |
| --- | --- |
| Najważniejszy niezmiennik | Retry tej samej komendy nie może utworzyć drugiej logicznej wysyłki, a wynik `UNKNOWN` providera nie może uruchomić ślepego fallbacku. |
| Krytyczny przepływ | Przyjęcie intencji → atomowa idempotencja i outbox → job z przypiętym template'em → ponowny consent check → provider → podpisany webhook → status kanałów. |
| Rekomendowany test | `mvnw.cmd -Dtest=NotificationGuaranteesTest test`; semantyka blokad: `mvnw.cmd -Pinfrastructure-tests verify`. |
| Świadome ograniczenia | Domyślny profil jest szybką symulacją in-memory, natomiast `postgres-runtime` uruchamia trwały rdzeń z lease'ami. Adapter HTTP providera jest wykonywalnym kontraktem, lecz domyślne routowanie nadal używa symulacji. IAM, KMS, broker i regionalne DR pozostają granicami produkcyjnymi. |

## Mapa wykonywalnych gwarancji

| Problem | Mechanizm w kodzie | Test | Granica produkcyjna |
| --- | --- | --- | --- |
| retry klienta | fingerprint i atomowy `insertIfAbsent` | `sameIdempotencyKeyWithDifferentRequestIsAConflict` | unikalny constraint w PostgreSQL i retencja odpowiedzi |
| replay outboxa | unikalny job `notification + channel` | `replayingOutboxDoesNotCreateSecondJobForTheSameChannel` | transakcja DB i broker at-least-once |
| timeout providera | `NOT_SENT` kontra `UNKNOWN` oraz reconciliation | `ambiguousProviderTimeoutDoesNotFallBackOrRetry`, `reconciliationTurnsAmbiguousAttemptIntoProviderAcceptedState` | prawdziwe status API providera |
| opt-out po zakolejkowaniu | drugi consent check i suppression po bounce | `marketingOptOutAfterQueueingStillStopsTheExternalEffect` | propagacja complaint/invalid token od realnych providerów |
| zmiana template'u | `templateId + version` przypięte do joba | `jobPinsTemplateVersionAndScheduledJobIsNotReadyEarly` | immutable template store |
| nielegalny status | jawna maszyna stanów | `stateMachineRejectsDeliveryBeforeProviderAcceptedTheJob` | optimistic locking/version column |
| wielu publisherów | `FOR UPDATE SKIP LOCKED`, retry schedule i `DEAD` po wyczerpaniu budżetu | `PostgreSqlNotificationInfrastructureIT`, `outboxEventuallyBecomesDeadInsteadOfRetryingForever` | kontrolowany replay i alert dla wieku outboxa |
| awaria workera po claimie | czasowy lease na rekordzie joba; po wygaśnięciu rekord wraca do puli | `durableQueueLeasePreventsConcurrentClaimAndAllowsCrashRecovery` | heartbeat dla operacji dłuższych od lease'u |
| webhook spoofing/replay | HMAC dokładnego body, timestamp, rotacja current/previous secret i trwały inbox po `eventId` | `WebhookSignatureVerifierTest`, `webhookInboxMakesProviderRedeliveryIdempotent` | KMS i kontrolowane wycofanie starego klucza |
| webhook nieuporządkowany | terminalny stan joba nie jest cofany przez późniejszy sprzeczny callback | `laterConflictingWebhookCannotDowngradeDeliveredState` | wersja/sekwencja, jeśli provider ją udostępnia |
| poison callback | retry z budżetem i stan `dead`, bez blokowania całego skanu | `poisonWebhookStopsAfterItsRetryBudget` | panel naprawczy i selektywny replay |
| historia prób | osobny `DeliveryAttempt` dla każdego wywołania providera | `ambiguousProviderTimeoutDoesNotFallBackOrRetry` | trwały append-only store i retencja audytowa |
| transakcja akceptacji | PostgreSQL: Notification + outbox w jednej transakcji | `apiAcceptanceAndOutboxRelayUseTheSamePostgresSourceOfTruth` | HA/backup bazy |
| agregacja kanałów | jedna `NotificationStatusPolicy` | `aggregateDistinguishesPartialDeliveryFromTotalFailureAndUnknown` | produktowa definicja sukcesu per typ |
| czas lokalny | jawna polityka DST, overlap i misfire | `dstGapAndOverlapAreResolvedByExplicitPolicy` | trwały scheduler i kalendarze użytkowników |
| przeciążenie | bounded inflight per tenant i provider | `admissionIsBoundedPerTenantAndReleasedDeterministically` | globalne limity rozproszone |
| usunięcie PII | anonimizacja treści/contact points z zachowaniem śladu technicznego | `erasureRemovesPayloadAndContactButPreservesTechnicalHistory` | backup erasure i polityka prawna |
| kontrakt providera | adapter HTTP, timeout, stabilny `Idempotency-Key`, klasyfikacja `NOT_SENT`/`UNKNOWN`/permanent | `HttpNotificationProviderTest`, `ProviderContractTest` | sandboxy i status API prawdziwych providerów |
| kampania i digest po restarcie | trwały checkpoint kampanii oraz lease bufora digestu | `campaignCheckpointAndDigestClaimSurviveRepositoryRoundTrip` | zewnętrzny blob odbiorców i osobny capacity pool |
| bezpieczeństwo wielodostępne | tenant-scoped reads i rola `ADMIN` dla control plane | `tenantCannotReadAnotherTenantsNotification`, `shouldRejectAdminEndpointWithoutTrustedRole` | tożsamość z gateway/mTLS zamiast zaufania dowolnemu nagłówkowi |
| równoległy start kampanii | claim token + lease + checkpoint sprawdzający właściciela | `campaignClaimAllowsOnlyOneConcurrentBatchOwner` | renewal lease'u dla długiego batcha |
| crash po efekcie providera | odzyskany `PROCESSING` przechodzi do `UNKNOWN`, nigdy do ślepego retry | `crashAfterProviderAcceptanceRecoversAsUnknownWithoutSecondExternalCall` | status API providera i ręczna eskalacja |
| pełna granica HTTP | profil `http-provider`, timeout i idempotency header w całym pipeline | `HttpProviderPipelineTest` | TLS, DNS, proxy i sandbox konkretnego vendora |
| tożsamość kryptograficzna | profil `secure-runtime` bierze tenant i role z podpisanego JWT | `SecureRuntimeApiTest` | asymetryczny klucz IdP, JWKS rotation i workload identity |
| specyfika kanałów | reputacja email, segmentacja SMS, token push i idempotentny unread | `ChannelSemanticsTest` | adaptery i raporty rzeczywistych sieci/providerów |
| kontrakt telemetryczny | test nazw metryk i ochrony cardinality | `ObservabilityContractTest` | walidacja PromQL przeciwko uruchomionemu Prometheusowi |

## Co rzeczywiście uruchamia projekt

Scheduler jest domyślnie włączony (`notification.scheduling.enabled=true`).
Testy sterujące ręcznie outboxem i workerem ustawiają tę właściwość na `false`,
aby callbacki `@Scheduled` nie przetwarzały równolegle tych samych jobów.
Duże `fixed-delay` nie wyłącza schedulera — nie opóźnia pierwszego uruchomienia.
Wyłączenie schedulera pozostawia serwisy dostępne do jawnych wywołań; w zwykłym
uruchomieniu aplikacji należy zachować domyślną wartość.

Projekt ma kilka celowo kompozycyjnych profili:

| Profil | Zastosowanie | Gwarancje | Czego nie dowodzi |
| --- | --- | --- | --- |
| domyślny | szybka nauka domeny i pipeline'u | maszyny stanów, idempotencja procesu, retry/fallback, consent, kampanie | restart procesu i koordynacja wielu instancji |
| `postgres-runtime` | wykonywalny rdzeń trwały | transaction acceptance, unique constraints, lease jobów i digestów, trwałe kampanie/attempty/inbox/suppression, `SKIP LOCKED` | broker wake-up i rzeczywisty control plane providerów |
| `http-provider` | cały pipeline przez prawdziwy socket HTTP | connect/request timeout, idempotency key, klasyfikacja odpowiedzi | semantyka konkretnego API, TLS i status endpoint |
| `secure-runtime` | podpisany JWT zamiast zaufanych nagłówków | authentication, tenant claim, `ROLE_ADMIN`, fail-closed | pełny IdP, JWKS rotation i polityka service-to-service |

W profilu domyślnym `InMemoryNotificationQueue` jest szybkim sygnałem gotowości do pracy. W profilu
`postgres-runtime` zastępuje ją `PostgresNotificationQueue`: atomowo wybiera należny job przez
`FOR UPDATE SKIP LOCKED`, zapisuje `lease_until`, a po awarii właściciela pozwala przejąć rekord dopiero po
wygaśnięciu lease'u. Broker może nadal zmniejszać opóźnienie wake-up, lecz nie jest wtedy źródłem prawdy.

### Zakres implementacji i out of scope

Kod celowo wykonuje mechanizmy, których nie da się wiarygodnie wyjaśnić samym diagramem: atomową akceptację,
idempotencję, outbox/inbox, lease, crash windows, retry/fallback, consent, webhook security, kampanie, digesty,
metryki, JWT oraz kontrakt HTTP. Nie próbuje lokalnie udawać całej infrastruktury globalnego przedsiębiorstwa.

Poza zakresem wykonywalnej implementacji pozostają: prawdziwe konta SendGrid/Twilio/FCM/APNs, zarządzanie DNS i
reputacją domen, Kafka/SQS, wieloregionalna baza, KMS/HSM, zewnętrzny IdP/JWKS, data warehouse, billing i prawny
workflow consentu. README opisuje ich wpływ i granice, ale nie oznacza ich jako zaimplementowanych. Profile można
łączyć, np. `postgres-runtime,http-provider,secure-runtime`, aby stopniowo zastępować symulacje realnymi granicami.

### Mapa odpowiedzialności w kodzie

- osobne kontrolery reprezentują public API, control plane, webhooki i operacje;
- `NotificationService` przyjmuje intencję i zamyka lokalną transakcję z outboxem;
- `OutboxPublisher` materializuje idempotentne joby;
- `NotificationWorker` zarządza lifecycle joba, a `ProviderDeliveryExecutor` izoluje routing, admission, fallback i
  historię zewnętrznych prób;
- repozytoria in-memory są szybką symulacją, a adaptery JDBC dowodzą zachowania zależnego od PostgreSQL;
- reconciliation i webhook inbox budują evidence plane niezależnie od ścieżki wysyłki.

## Model mentalny systemu

Notification System nie jest wrapperem na SendGrid lub FCM. Jest systemem zarządzającym **intencją komunikacji**,
jej polityką, próbami wykonania i dowodami dostarczenia. Trzeba rozdzielać pięć pojęć:

1. `Notification` — biznesowa intencja dotarcia do odbiorcy;
2. `NotificationJob` — wykonanie tej intencji konkretnym kanałem;
3. `DeliveryAttempt` — pojedyncze wywołanie konkretnego providera;
4. `provider message id` — identyfikator operacji po stronie zależności;
5. delivery event — późniejsza obserwacja `DELIVERED`, `BOUNCED`, complaint lub invalid token.

`202 Accepted` znaczy wyłącznie, że system trwale przyjął odpowiedzialność za dalsze przetwarzanie. `SENT` znaczy,
że provider przyjął request. Nawet `DELIVERED` ma znaczenie kanałowe: dla SMTP zwykle opisuje przyjęcie przez serwer
docelowy, a dla push nie dowodzi przeczytania wiadomości przez człowieka. Open/click tracking jest sygnałem
analitycznym, może być blokowany i nie powinien sterować krytyczną logiką biznesową.

## Jak rozumować o Notification System

Najważniejszym krokiem projektowym jest oddzielenie **decyzji biznesowej o komunikacji** od technicznej próby
wysłania bajtów. Bez tego rozdzielenia jedna tabela `notifications` zaczyna jednocześnie reprezentować request,
wiadomość email, retry, callback providera i wynik całej operacji. Wtedy nie da się jednoznacznie odpowiedzieć,
czy ponowienie jest bezpieczne ani co oznacza status `FAILED`.

W tym projekcie odpowiedzialność jest rozłożona na kolejne poziomy:

| Poziom | Pytanie, na które odpowiada | Typowa cardinality | Source of truth |
| --- | --- | --- | --- |
| `Notification` | co system biznesowy chciał zakomunikować? | 1 na komendę biznesową | baza intencji |
| `NotificationJob` | którym kanałem realizujemy intencję? | 0..N na Notification | trwały store jobów |
| `DeliveryAttempt` | którego providera faktycznie wywołaliśmy? | 0..N na job | append-only historia prób |
| provider operation | jaki efekt mógł zajść poza naszym systemem? | 0..1 na attempt | system providera |
| delivery event | co później zaobserwował provider? | 0..N na operację | inbox callbacków + historia |

To rozdzielenie daje dwie ważne własności. Retry joba nie usuwa historii wcześniejszych prób, a callback zmienia
wiedzę o dostawie bez udawania, że cofa wcześniej wykonane wywołanie. Status jest więc **stanem wiedzy systemu**, a
nie absolutną prawdą o tym, czy człowiek zobaczył komunikat.

### Control plane i data plane

W większej instalacji warto rozdzielić dwie płaszczyzny:

- control plane zarządza template'ami, konfiguracją providerów, regułami routingu, limitami, kampaniami i sekretami;
- data plane przyjmuje intencje, tworzy joby, renderuje wiadomości, wywołuje providerów i przetwarza callbacki.

Control plane ma mały ruch, ale wymaga silnej autoryzacji, audytu i bezpiecznej propagacji konfiguracji. Data plane
ma duży ruch i jest optymalizowany pod izolację awarii, backpressure oraz szybki hot path. Awaria panelu template'ów
nie powinna zatrzymywać wysyłki już przypiętych wersji, dlatego job przechowuje `templateId` i `templateVersion`.

## Semantyka kanałów — wspólny pipeline, różne gwarancje

Email, SMS, push i in-app nie są jedynie czterema adapterami z takim samym kontraktem. Różnią się kosztem,
opóźnieniem, informacją zwrotną, sposobem identyfikacji odbiorcy i znaczeniem „dostarczono”.

| Właściwość | Email | SMS | Push | In-app |
| --- | --- | --- | --- | --- |
| koszt marginalny | niski/średni | zwykle wysoki | niski | infrastruktura własna |
| identyfikator odbiorcy | adres email | numer E.164 | zmienny token urządzenia | user/account ID |
| potwierdzenie | accepted, bounce, complaint | accepted/delivered zależne od operatora | accepted/invalid token | zapis, odczyt klienta |
| ordering | zwykle best effort | best effort | best effort per urządzenie | można zapewnić per użytkownik |
| częsty błąd trwały | invalid mailbox | invalid/blocked number | expired token | usunięty użytkownik |
| szczególna ochrona | SPF/DKIM/DMARC, unsubscribe | quiet hours, koszt, regulacje | lifecycle tokenu, TTL | unread count, synchronizacja urządzeń |

Wspólny model upraszcza orkiestrację, lecz polityka retry, TTL, rate limit i status końcowy muszą pozostać kanałowe.
Przykładowo retry SMS-a może zwiększyć koszt i ryzyko duplikatu, a retry push po `invalid token` jest bezcelowy.

## Granice transakcji i model spójności

Notification System przecina trzy niezależne źródła prawdy: lokalną bazę, broker oraz system providera. Nie istnieje
jedna transakcja ACID obejmująca te granice. Poprawność uzyskuje się przez lokalne transakcje, idempotencję i procesy
naprawcze:

1. przyjęcie `Notification` oraz wpisu outbox następuje w jednej transakcji lokalnej;
2. relay może opublikować outbox więcej niż raz;
3. konsument tworzy job idempotentnie dzięki kluczowi `(notification_id, channel)`;
4. wywołanie providera otrzymuje stabilny klucz operacji, jeśli provider go obsługuje;
5. callback jest deduplikowany i nie może cofnąć stanu do mniej zaawansowanego;
6. brak callbacku lub wynik `UNKNOWN` jest naprawiany przez reconciliation, nie przez założenie sukcesu albo porażki.

Z punktu widzenia klienta przyjęcie komendy może być liniowalne w granicy rekordu idempotencji, natomiast status
dostawy jest eventual consistent. API powinno więc zwrócić identyfikator zasobu i pozwolić odpytać stan zamiast
blokować request HTTP aż do odpowiedzi wszystkich providerów.

### Dlaczego „exactly once” jest niewłaściwym wymaganiem

Exactly-once można zrealizować dla **lokalnej zmiany stanu** chronionej constraintem. Nie można wiarygodnie obiecać
exactly-once dla dowolnego efektu zewnętrznego, jeśli po timeoutcie nie wiadomo, czy provider wykonał operację.
Poprawniejszy kontrakt brzmi:

- exactly-once acceptance dla tej samej pary tenant + idempotency key;
- at-least-once transport outboxa i brokera;
- idempotentne materializowanie joba;
- co najmniej jedna próba dostawy, z możliwością wyniku niejednoznacznego;
- deduplikowane i monotoniczne przetwarzanie obserwacji providera.

## Maszyna stanów i agregacja wielu kanałów

Statusy muszą tworzyć dozwolony graf przejść, a nie dowolnie edytowalną kolumnę tekstową. Przykładowy happy path
joba wygląda tak:

```text
QUEUED -> PROCESSING -> SENT -> DELIVERED
             |            |
             |            +-> BOUNCED / FAILED
             +-> QUEUED (retry po wyniku NOT_SENT)
             +-> UNKNOWN (efekt zewnętrzny mógł zajść)
```

`UNKNOWN` nie jest rodzajem `FAILED`. Jest stanem wymagającym dodatkowego dowodu. Automatyczne przejście
`UNKNOWN -> QUEUED` może wygenerować duplikat, dlatego potrzebuje odpytywania providera, ręcznej decyzji albo
udokumentowanej akceptacji ryzyka dla danego rodzaju komunikatu.

Status nadrzędnego `Notification` jest projekcją, a nie prostym kopiowaniem statusu ostatniego joba:

| Stany kanałów | Sensowny stan agregatu |
| --- | --- |
| wszystkie `QUEUED` | `QUEUED` |
| dowolny `PROCESSING`, brak wyniku końcowego | `PROCESSING` |
| wszystkie przyjęte przez providerów | `SENT` |
| wszystkie potwierdzone | `DELIVERED` |
| część dostarczona, reszta terminalnie niedostarczona | `PARTIALLY_DELIVERED` |
| choć jeden kanał `UNKNOWN` | `UNKNOWN` lub osobny stan częściowo nieznany |
| wszystkie kanały terminalnie niedostarczone | `FAILED` |

Polityka „sukces, jeśli dowolny kanał dostarczył” bywa właściwa dla alertu, ale niewłaściwa dla komunikatu, którego
każdy kanał ma osobne znaczenie prawne. Agregacja powinna być częścią kontraktu produktu, nie przypadkowym `if`.

## Routing providerów i bezpieczny fallback

Provider routing może uwzględniać kanał, kraj, koszt, historyczny success rate, latency, typ ruchu i wymagania
compliance. Najprostsza lista primary/fallback jest wystarczająca edukacyjnie, ale produkcyjny wybór powinien mieć
stabilną politykę i ograniczać oscylację między providerami.

Fallback jest bezpieczny wyłącznie wtedy, gdy poprzednia próba na pewno nie mogła wywołać efektu (`NOT_SENT`).
Timeout po wysłaniu requestu daje `UNKNOWN`; wywołanie drugiego providera może wtedy dostarczyć drugą wiadomość.
Circuit breaker chroni zasoby systemu, lecz nie rozstrzyga wyniku konkretnej operacji. Retry, fallback i circuit
breaker rozwiązują inne problemy i nie powinny być traktowane zamiennie.

## Kolejność, partycjonowanie i backpressure

Globalna kolejność wszystkich powiadomień byłaby droga i zwykle niepotrzebna. Jeśli produkt wymaga kolejności,
należy nazwać jej zakres, np. `(tenantId, userId, channel)`, i użyć tego samego klucza partycji. Nadal trzeba jednak
obsłużyć opóźniony retry: starszy job może wrócić po wysłaniu nowszego. Dla komunikatów zależnych semantycznie lepszy
bywa numer sekwencji lub odrzucanie nieaktualnych wersji niż poleganie wyłącznie na kolejności brokera.

Backpressure zaczyna się przed kolejką. API może zastosować admission control, kampanie muszą być porcjowane, a
worker powinien mieć bounded concurrency per provider. Nieskończona kolejka tylko przesuwa awarię w czasie:
zwiększa wiek wiadomości, koszty storage, liczbę nieaktualnych komunikatów i czas odtwarzania po incydencie.

## Prywatność, retencja i audyt

Payload wiadomości może zawierać PII, dane finansowe lub link resetujący hasło. Z tego powodu warto rozdzielić:

- metadane routingu potrzebne do dostawy;
- treść wymagającą krótkiej retencji lub szyfrowania pole-level;
- log audytowy opisujący kto i dlaczego zainicjował komunikację;
- dane analityczne, które powinny być zanonimizowane lub agregowane.

Logi nie powinny zawierać pełnego adresu, numeru telefonu, tokenu push, payloadu ani sekretu webhooka. Retencja
attemptów może być inna niż retencja treści. Usunięcie danych użytkownika musi objąć joby, cache, suppression listy,
analitykę i kopie zapasowe zgodnie z udokumentowaną polityką, bez niszczenia wymaganych prawnie dowodów audytowych.
Laboratorium udostępnia `DELETE /api/v1/admin/users/{userId}/personal-data`: usuwa preferencje i suppression,
anonimizuje payload/contact points oraz pseudonimizuje aktora w audycie, zachowując techniczną historię dostawy.
Usunięcie z backupów pozostaje procesem retencyjnym — natychmiastowa edycja każdej kopii mogłaby zniszczyć jej
integralność i zdolność do odtworzenia.

## Gwarancje dostarczenia i okna awarii

End-to-end exactly-once nie jest realną obietnicą przy sieci i zewnętrznym providerze. Projekt przyjmuje:

- at-least-once dla outboxa i kolejki;
- idempotentne tworzenie jobów;
- stabilny provider idempotency key dla kolejnych prób tej samej operacji;
- brak automatycznego fallbacku po niejednoznacznym timeoutcie;
- reconciliation dla wyników `UNKNOWN` i `SENT` bez callbacku.

`DeliveryReconciliationService` wykonuje tę politykę na deterministycznej symulacji status API: może uznać operację
za przyjętą, potwierdzić końcowe dostarczenie, bezpiecznie wznowić wynik `NOT_SENT` albo pozostawić `STILL_UNKNOWN`.
Skan obejmuje zarówno niejednoznaczne joby `UNKNOWN`, jak i `SENT` czekające zbyt długo na callback. Granicą produkcyjną jest adapter
do rzeczywistego endpointu providera, jego rate limit, retencja identyfikatorów i eskalacja zbyt długo nieznanych prób.

Najważniejsze okna awarii:

| Awaria | Ryzyko | Wymagana ochrona |
| --- | --- | --- |
| po zapisie Notification, przed outboxem | zgubiona wysyłka | wspólna transakcja |
| po utworzeniu joba, przed ack outboxa | podwójny job | unique `(notification_id, channel)` |
| po wywołaniu providera, przed zapisem odpowiedzi | podwójna wiadomość | `UNKNOWN`, provider key, reconciliation |
| po zapisie retry, przed enqueue | job bez wake-up | trwały scheduler skanujący due jobs |
| callback przed zapisem provider ID | niedopasowany status | inbox pending events i późniejsze dopasowanie |

## Priorytet, fairness i backpressure

Kampania nie może wyprzeć OTP ani alertu bezpieczeństwa. Same etykiety priorytetu nie wystarczą. Potrzebne są
oddzielne capacity pools lub kolejki, weighted fair scheduling, limit inflight per provider i per tenant oraz
admission control. Najważniejszą metryką backlogu jest **wiek najstarszego gotowego joba**, nie sama liczba rekordów.
Skalowanie workerów ma sens tylko do granicy przepustowości bazy i providera; powyżej niej zwiększa retry storm.

## Consent, suppression i polityka kanału

Polityka powinna jawnie rozróżniać komunikację bezpieczeństwa, transakcyjną, produktową i marketingową.
Preferencja użytkownika, wymóg prawny i suppression providera nie są tym samym. Dla marketingu opt-out musi wygrać
z wcześniej utworzonym jobem, dlatego jest sprawdzany tuż przed efektem. Hard bounce, spam complaint i invalid push
token powinny zasilać suppression list. Link unsubscribe musi być podpisany, ograniczony zakresem i odporny na replay.

## Czas, scheduling i kampanie

`scheduledAt` przechowujemy jako `Instant`. Żądanie „9:00 czasu użytkownika” wymaga jednak także identyfikatora
`ZoneId` i jawnej polityki dla brakującej/podwójnej godziny DST. Po restarcie scheduler musi odszukać należne rekordy;
in-memory timer nie jest źródłem prawdy. Misfire policy określa, czy opóźnioną kampanię wysłać, pominąć czy wygasić.
Kampanie przetwarza się porcjami z checkpointem, limitem kosztu, pause/cancel i osobną pulą zasobów. Digest wymaga
atomowego claimu, limitu elementów i bezpiecznego wznowienia po awarii.

## Capacity estimation — przykładowe założenia

Załóżmy 10 mln użytkowników, 5 komunikatów dziennie i peak 50 tys. intencji/minutę. Daje to około 579 intencji/s
średnio i 833/s w podanym peaku. Jeśli jedna intencja rozwija się średnio do 1,6 kanału, peak jobów wynosi około
1333/s. Kampania do 5 mln odbiorców uruchomiona w godzinę wymaga około 1389 intencji/s jeszcze przed ruchem
transakcyjnym — dlatego musi być throttlowana.

Przy 2 KB metadanych na intencję i 1 KB na job dzienny przyrost bazowy to około 180 GB przed indeksami, audytem,
replikacją i payloadami. Treści oraz duże listy odbiorców lepiej trzymać w object storage, a w bazie przechowywać
referencje. Capacity plan powinien osobno policzyć request rate, fan-out kanałów, callback rate, bandwidth i koszt SMS.

## SLO i obserwowalność

SLO należy definiować per klasa ruchu, przykładowo:

- critical: 99% zaakceptowanych intencji przekazane providerowi w 30 s;
- transactional: 99% w 2 min;
- marketing: 99% w oknie kampanii, bez naruszania budżetu ruchu krytycznego.

SLI mierzy się od zapisu intencji do `SENT`, osobno od `SENT → DELIVERED`. Dashboard powinien pokazywać histogramy,
oldest-job age, outbox lag, saturation, retry, `UNKNOWN`, fallback, bounce/complaint, DLQ oraz koszt per tenant. Gotowe
reguły znajdują się w `ops/prometheus-alerts.yml`, dashboard w `ops/dashboard.json`, a procedury w `ops/RUNBOOK.md`.

## High availability, disaster recovery i regiony

HA chroni przed awarią instancji lub availability zone, a DR przed utratą całego regionu albo logicznym
uszkodzeniem danych. Replika PostgreSQL nie jest backupem: może powielić przypadkowy delete i błędną migrację.
Plan powinien zdefiniować osobno:

- RPO dla intencji i audytu — ile zaakceptowanych rekordów wolno utracić;
- RTO — jak długo może nie działać przyjmowanie oraz jak długo może rosnąć backlog;
- backup, point-in-time recovery i regularny restore drill;
- zachowanie DNS/gateway podczas failoveru oraz sposób uniknięcia aktywnych writerów w dwóch regionach;
- los jobów `PROCESSING` podczas przejęcia oraz ponowną rekoncyliację z providerem;
- regionalność danych kontaktowych, template'ów, sekretów i kluczy szyfrujących.

Najprostszy bezpieczny wariant to active-passive dla zapisu z asynchroniczną replikacją i kontrolowanym failoverem.
Active-active wymaga globalnej idempotencji, jednoznacznego ownership partycji i rozwiązania konfliktów; sama baza
multi-region nie zapobiega dwóm równoległym wywołaniom providera. Po przełączeniu regionu należy najpierw odtworzyć
źródło prawdy, następnie relay outboxa, skan należnych jobów i reconciliation, a dopiero potem zwiększać throughput.

## Ewolucja kontraktów i wdrożenia bez przestoju

Request API, event outboxa, payload kolejki i webhook providera są niezależnymi kontraktami. Zmiana powinna być
additive: nowe pola opcjonalne, tolerowanie nieznanych pól, wersja schematu i okres równoczesnej obsługi producenta
oraz konsumenta. Migracje danych stosują expand/contract — najpierw nowa kolumna i kod zdolny czytać oba formaty,
potem backfill, przełączenie zapisu, a dopiero na końcu usunięcie starego pola.

Rolling deployment oznacza, że przez pewien czas działają co najmniej dwie wersje workera. Template przypięty do
joba oraz monotoniczna maszyna stanów ograniczają zależność wyniku od chwili rollout'u. Breaking change webhooka
powinien mieć osobny adapter wersji, a nie warunek rozsiany po logice domenowej.

## Jak weryfikować taki system

Same testy jednostkowe nie dowodzą gwarancji rozproszonej. Sensowna strategia obejmuje:

1. testy domenowe maszyn stanów, polityki czasu i agregacji;
2. property-based lub model-based tests losowych sekwencji retry/callback/cancel;
3. contract tests wszystkich provider adapterów;
4. Testcontainers dla constraintów, transakcji i `SKIP LOCKED`;
5. scenariusze crash-after-effect, crash-before-ack i callback-before-response;
6. load, spike i soak test z obserwacją oldest-job age, saturation i kosztu;
7. game day: niedostępny provider, rosnący backlog, utrata brokera oraz restore bazy;
8. okresową weryfikację alertu i runbooka, nie tylko obecność plików konfiguracyjnych.

Kryterium sukcesu eksperymentu nie brzmi „wszystkie requesty HTTP zwróciły 2xx”. Należy sprawdzić niezmienniki:
brak podwójnej intencji, brak nieautoryzowanej wysyłki, kontrolowany backlog, monotoniczne statusy oraz możliwość
wyjaśnienia każdej próby na podstawie historii.

## Granica uwierzytelnienia i tenanta

W profilu domyślnym `X-Tenant-Id` i `X-Role` reprezentują nagłówki nadane przez zaufany gateway. To szybka symulacja,
nie mechanizm uwierzytelnienia. Profil `secure-runtime` włącza Spring Security Resource Server: bez Bearer JWT request
jest odrzucany, tenant pochodzi wyłącznie z podpisanego claimu `tenant_id`, a control plane wymaga `ROLE_ADMIN`.
Przesłany równocześnie `X-Tenant-Id` nie może nadpisać claimu.

Lokalny profil używa HMAC, aby test pozostał samodzielny. Produkcyjny wariant powinien ufać asymetrycznym kluczom
IdP przez JWKS, obsługiwać rotację `kid`, sprawdzać issuer/audience oraz używać workload identity lub mTLS dla
service-to-service. Tożsamość podmiotu i powód administracyjnej operacji powinny trafić do audytu.

## Fundamenty teoretyczne projektowania Notification System

### System intencji, a nie tylko system transportowy

Najczęstszy błąd projektowy polega na utożsamieniu Notification System z biblioteką `sendEmail()`. Transport jest
jedynie ostatnią częścią procesu. System musi wcześniej odpowiedzieć na pytania: czy komunikacja jest dozwolona,
który kanał ma sens, jaka wersja treści obowiązuje, kiedy komunikat traci wartość oraz co zrobić, gdy nie wiadomo,
czy zewnętrzny efekt zaszedł. Z tego wynika podział na cztery płaszczyzny:

| Płaszczyzna | Odpowiedzialność | Przykładowe dane | Dominująca gwarancja |
| --- | --- | --- | --- |
| intention plane | przyjęcie biznesowej intencji | typ, odbiorca, idempotency key, deadline | exactly-once acceptance w granicy lokalnego constraintu |
| policy plane | consent, routing, priorytet, quiet hours | preferencje, suppression, reguły kanału | decyzja audytowalna i ponawialna |
| delivery plane | wykonanie prób i kontrola obciążenia | job, attempt, lease, provider key | at-least-once processing bez ślepego retry efektu `UNKNOWN` |
| evidence plane | późniejsza wiedza o skutku | callback, bounce, complaint, reconciliation | deduplikacja i monotoniczna aktualizacja wiedzy |

Takie rozdzielenie pozwala skalować i naprawiać każdą płaszczyznę niezależnie. Przykładowo awaria panelu
template'ów nie musi zatrzymywać wysyłki już przypiętych wersji, a opóźniony webhook nie powinien blokować przyjęcia
nowej intencji.

### Źródła prawdy i projekcje

W systemie rozproszonym nie istnieje jedna tabela zawierająca całą prawdę. Trzeba jawnie określić właściciela
każdego faktu:

- lokalna baza jest źródłem prawdy o przyjętej intencji, konfiguracji joba i historii prób;
- Preference/Consent Service jest źródłem prawdy o zgodzie użytkownika;
- provider jest źródłem prawdy o tym, czy zaakceptował operację i jaki status później zaobserwował;
- read model statusu powiadomienia jest projekcją powyższych faktów, a nie niezależną prawdą;
- broker transportuje informację, ale nie powinien być jedynym dowodem przyjęcia biznesowej intencji.

Status `SENT` jest zatem twierdzeniem epistemicznym: „mamy dowód, że provider przyjął operację”. `UNKNOWN` znaczy
„nie mamy wystarczającego dowodu”, a nie „operacja się nie udała”. To rozróżnienie jest podstawą bezpiecznego retry.

### Drabina gwarancji dostarczenia

Hasła at-most-once, at-least-once i exactly-once mają sens dopiero po wskazaniu konkretnej granicy:

| Granica | Realistyczna gwarancja | Mechanizm | Pozostałe ryzyko |
| --- | --- | --- | --- |
| klient → API | exactly-once acceptance dla jednego klucza | fingerprint + unique constraint + zapis odpowiedzi | retencja klucza krótsza od retry klienta |
| baza → relay | at-least-once | transactional outbox | wielokrotna publikacja eventu |
| relay → job | effectively-once materialization | unique `(notificationId, channel)` | konsument nadal musi tolerować replay |
| worker → provider | zależne od API providera | stabilny operation key + klasyfikacja pewności | timeout po efekcie daje `UNKNOWN` |
| provider → webhook | at-least-once i możliwy reorder | inbox po `eventId` + monotoniczna maszyna stanów | brak callbacku lub sprzeczne źródła |
| cały proces | eventual convergence | reconciliation + historia prób | ręczna decyzja dla długo nierozstrzygniętego efektu |

„Effectively once” nie jest własnością transportu. Jest wynikiem złożenia at-least-once delivery z trwałą,
atomową deduplikacją efektu u konsumenta. Jeśli efekt jest poza kontrolą konsumenta, gwarancja zależy od kontraktu
idempotencji oferowanego przez providera.

### Teoria kolejki, capacity i stabilność systemu

System jest stabilny tylko wtedy, gdy długookresowa przepustowość obsługi `μ` jest większa od tempa napływu `λ`.
Gdy `λ >= μ`, żadna liczba retry ani większy broker nie rozwiąże problemu — backlog będzie rósł do wyczerpania
retencji, storage'u albo ważności komunikatów. Broker kupuje czas na absorpcję krótkiego burstu, nie tworzy capacity.

Prawo Little'a łączy średnią liczbę elementów `L`, throughput `λ` i średni czas w systemie `W`:

```text
L = λ × W
```

Jeżeli napływa 1000 jobów/s, a przeciętny job spędza 30 s w pipeline, należy oczekiwać około 30 000 jobów in-flight.
To przybliżenie pomaga wykryć niespójne estymacje i dobrać liczbę partycji, połączeń oraz retencję. Nie zastępuje
pomiaru rozkładu opóźnień — średnia ukrywa p95/p99 i starvation niskiego priorytetu.

W pobliżu pełnej saturacji latency rośnie nieliniowo. Dlatego bezpieczny system utrzymuje zapas, ogranicza inflight i
stosuje load shedding zanim zużyje wszystkie połączenia. Skalowanie workerów zatrzymuje się na najmniejszym z limitów:

```text
min(DB write capacity, provider quota, network capacity, worker capacity, budget kosztowy)
```

Retry zwiększa efektywny ruch. Przy średnio `r` dodatkowych próbach rzeczywiste zapotrzebowanie wynosi w przybliżeniu
`λ × (1 + r)`. Podczas awarii zależności `r` rośnie właśnie wtedy, gdy jej capacity maleje, dlatego potrzebne są
exponential backoff, jitter, retry budget, circuit breaker oraz bounded concurrency.

### Priorytety nie gwarantują fairness

Ścisła kolejka priorytetowa może zagłodzić marketing, a pojedyncza kolejka FIFO może dopuścić, aby wielka kampania
opóźniła OTP. Typowe rozwiązania to osobne capacity pools, weighted fair queueing i limity per tenant. Wagi określają
udział przepustowości, ale krytyczny ruch powinien mieć również zarezerwowane minimum.

Noisy-neighbor pojawia się nie tylko na kolejce. Jeden tenant może zużyć provider quota, pulę połączeń, miejsce w
outboxie albo budżet SMS. Z tego powodu fairness musi działać na każdym ograniczonym zasobie. Tagowanie metryk
`tenantId` jest jednak niebezpieczne ze względu na cardinality; szczegółowy koszt per tenant lepiej obliczać z
agregowanych eventów billingowych lub okresowych raportów.

### Ordering, causality i ważność biznesowa

Globalna kolejność jest kosztowna i zwykle niepotrzebna. Najpierw trzeba zdefiniować zakres: per użytkownik, kanał,
konwersację czy encję biznesową. Nawet kolejność brokera nie wystarcza, gdy starszy komunikat trafia do retry i wraca
po nowszym. Jeśli kolejność ma znaczenie, payload powinien zawierać wersję lub numer sekwencji, a konsument powinien
umieć odrzucić stan przestarzały.

Nie każdy komunikat warto dostarczyć za wszelką cenę. OTP, kod logowania i informacja „kurier będzie za 5 minut” mają
deadline; wysłanie po nim jest błędem biznesowym, mimo że technicznie zakończy się sukcesem. TTL i `expiresAt` są
więc częścią poprawności, a nie jedynie optymalizacją storage'u.

### Delivery, deliverability i engagement

Trzy pojęcia nie mogą być używane zamiennie:

- **delivery** opisuje techniczny status kanału, np. przyjęcie przez serwer docelowy;
- **deliverability** opisuje zdolność docierania do skrzynki/urządzenia, zależną od reputacji, jakości listy i polityk;
- **engagement** opisuje zachowanie użytkownika, np. open/click/read, i jest sygnałem analitycznym o ograniczonej
  wiarygodności.

Email wymaga zarządzania reputacją domeny i IP, SPF/DKIM/DMARC, bounce oraz complaint rate. SMS zależy od kraju,
operatora, sender ID, regulacji i kosztu segmentów. Push wymaga lifecycle tokenów, topiców, TTL i zrozumienia, że
akceptacja przez FCM/APNs nie dowodzi wyświetlenia. Jeden wspólny pipeline powinien normalizować podstawowe statusy,
ale nie może usuwać kanałowych różnic.

### Model kosztu jako część architektury

Koszt powinien być ograniczeniem podobnie jak latency. Dla każdej klasy ruchu warto policzyć:

```text
koszt dzienny = liczba intencji × średni fan-out kanałów × średnia liczba prób × koszt próby
```

Do tego dochodzą callbacki, egress, retencja payloadów, monitoring i koszt operacyjny. Fallback do drugiego providera
może zwiększyć niezawodność, ale również podwoić koszt lub naruszyć umowę o danych regionalnych. Kampania powinna
mieć budżet, forecast i kill switch, zanim zacznie tworzyć miliony jobów.

### Kiedy budować, a kiedy kupić

Własny Notification System ma sens, gdy wiele produktów potrzebuje wspólnego consentu, wielokanałowego routingu,
audytu, kontroli kosztu, tenant isolation i niezależności od providera. Dla małego produktu z jednym kanałem lepszy
może być bezpośredni provider lub managed orchestration: mniejszy koszt utrzymania, szybszy start i mniej failure
modes.

Decyzja nie jest binarna. Częsty kompromis to własny intention/policy plane oraz zewnętrzny delivery provider.
Wtedy kluczowe pozostają idempotencja, statusy, reconciliation i możliwość eksportu danych. Vendor lock-in należy
oceniać przez semantykę szablonów, API statusowe, przenośność suppression list, dostęp do surowych eventów i koszt
historycznego replayu — nie tylko przez podobieństwo metody `send()`.

### Kompletna mapa wiedzy projektowej

Znajomość API providera nie oznacza jeszcze znajomości Notification System. Projektant powinien umieć odpowiedzieć
na wszystkie poniższe grupy pytań; dalsze rozdziały README rozwijają każdą z nich.

| Obszar | Pytania projektowe |
| --- | --- |
| wymagania | które komunikaty są krytyczne, jaki mają deadline, consent i definicję sukcesu? |
| kontrakt API | co znaczy `202`, jaki jest zakres idempotency key i jak klient odczyta eventual status? |
| model danych | co jest intencją, jobem, próbą i dowodem; które rekordy są immutable? |
| spójność | gdzie kończy się transakcja ACID i jak zamykamy każde dual-write/failure window? |
| delivery semantics | dla której granicy obiecujemy at-most-once, at-least-once lub effectively-once? |
| scheduling | jak obsługujemy UTC, strefę użytkownika, DST, misfire, TTL i anulowanie? |
| kanały | czym różni się provider acceptance, delivery, deliverability i engagement? |
| przeciążenie | jaki jest `λ/μ`, retry amplification, bounded inflight, fairness i load shedding? |
| skalowanie | jaki jest klucz partycji, ordering scope, hot tenant i strategia rebalancingu? |
| bezpieczeństwo | gdzie są trust boundaries, jak uwierzytelniamy callerów/webhooki i chronimy PII? |
| obserwowalność | jakie SLI mierzą acceptance, queue age, provider outcome i final delivery? |
| operacje | jak zatrzymać kampanię, replayować rekord, odtworzyć backup i przełączyć region? |
| koszt | jaki jest fan-out, liczba prób, koszt kanału i budżet kampanii? |
| ewolucja | jak wersjonujemy API, event, template, webhook oraz schemat bez przestoju? |
| testowanie | który test dowodzi inwariantu, a który tylko poprawnego happy path? |

## Wykonywalny lifecycle niezawodności

### Claim joba i odzyskanie po awarii

Worker nie „zdejmuje” wiadomości bezpowrotnie. PostgreSQL jest źródłem prawdy, a poller wykonuje krótki claim:

1. wybiera tylko `QUEUED`, należne rekordy bez aktywnego lease'u;
2. używa `FOR UPDATE SKIP LOCKED`, aby dwa workery nie czekały na ten sam rekord;
3. zapisuje `lease_until` i zwalnia transakcję przed wywołaniem sieciowym;
4. drugi worker omija rekord do chwili wygaśnięcia lease'u;
5. jeśli właściciel zginie, rekord znów staje się claimowalny.

Lease nie daje exactly-once external effect. Awaria po wysłaniu requestu, ale przed zapisem odpowiedzi nadal daje
wynik `UNKNOWN`. Dlatego trwały claim chroni koordynację workerów, natomiast provider idempotency key oraz
reconciliation chronią granicę efektu zewnętrznego. Operacja trwająca dłużej niż lease wymagałaby heartbeat/renewal.

`PostProviderEffectProbe` wykonuje najgroźniejsze okno awarii: provider zaakceptował efekt, ale proces ginie przed
lokalnym `SENT`. Job pozostaje `PROCESSING`; odzyskujący worker przeprowadza go do `UNKNOWN` i nie wywołuje providera
ponownie. Historia zachowuje jedną próbę `ACCEPTED`, a rozstrzygnięcie przejmuje reconciliation.

### Outbox: retry, backoff i stan `DEAD`

Outbox przechodzi `PENDING → PUBLISHED` albo, po wyczerpaniu limitu, `PENDING → DEAD`. Każde niepowodzenie zwiększa
licznik prób i ustawia `next_attempt_at` przez exponential backoff z deterministycznym jitterem. Dzięki temu relay
nie wykonuje hot loop i nie wywołuje retry storm. `DEAD` oznacza zatrzymanie automatycznych prób — operator musi
usunąć przyczynę, zachować ten sam identyfikator zdarzenia i wykonać selektywny replay. Nie wolno tworzyć nowego
logicznego eventu tylko po to, aby ominąć historię błędu.

### Inbox webhooków i monotoniczność

Callback jest najpierw uwierzytelniany HMAC-em dokładnego raw body i oknem timestampu, a potem zapisywany pod
unikalnym `eventId`. Inbox ma własne `attempts`, `nextAttemptAt` i stan poison/dead, więc jeden wadliwy event nie
blokuje partycji w nieskończoność. Stan terminalny joba nie jest cofany przez późniejszy callback sprzeczny z już
uzyskanym dowodem. Podczas rotacji akceptowane są current i previous secret; stary klucz należy usunąć po czasie
dłuższym od maksymalnego opóźnienia/retry callbacków.

## Granica prawdziwego providera HTTP

`HttpNotificationProvider` jest framework-independent adapterem kontraktu sieciowego. Ustawia request timeout i
stabilny `Idempotency-Key`, wymaga identyfikatora operacji w odpowiedzi 2xx oraz rozróżnia:

| Obserwacja | Pewność | Reakcja |
| --- | --- | --- |
| błąd połączenia przed ustanowieniem transportu | `NOT_SENT` | retry/fallback jest dopuszczalny |
| HTTP 429 lub 5xx | `NOT_SENT` w kontrakcie tego adaptera | backoff, ewentualnie fallback |
| HTTP 4xx | błąd trwały | bez automatycznego retry |
| timeout odpowiedzi / ogólny błąd I/O | `UNKNOWN` | bez ślepego retry i fallbacku; reconciliation |
| 2xx bez operation id | `UNKNOWN` | provider złamał kontrakt; reconciliation |

Klasyfikacja 429/5xx jako `NOT_SENT` jest poprawna tylko wtedy, gdy umowa providera gwarantuje brak efektu dla tych
odpowiedzi. Dla API, które może zwrócić 5xx po wykonaniu operacji, adapter musi klasyfikować taki wynik jako
`UNKNOWN`. To przykład, dlaczego semantyka zależności jest ważniejsza od samego kodu HTTP.

## Trwałe kampanie i digesty

Profil PostgreSQL przechowuje status kampanii, indeks następnego odbiorcy, liczbę błędów oraz immutable payload.
Checkpoint jest zapisywany po każdym odbiorcy, więc restart wznawia batch zamiast zaczynać go od zera. Nadal
obowiązuje idempotency key per kampania + odbiorca: transakcja checkpointu nie obejmuje zewnętrznej dostawy.

Start batcha używa `claim_token` i `claim_until`. Tylko właściciel tokenu może zapisać checkpoint, więc spóźniony
worker po przejęciu lease'u nie nadpisze postępu nowego właściciela. Jeśli crash nastąpi po utworzeniu Notification,
ale przed checkpointem, nowy właściciel powtórzy odbiorcę; idempotency key `campaignId:userId` zamienia replay w
odczyt istniejącej intencji. Lease rozwiązuje koordynację, a idempotencja powtórzenie lokalnego efektu.

Digest przechowuje bufor i `flush_at`, a due buffer jest claimowany przez `SKIP LOCKED` oraz czasowy `claim_until`.
To zapobiega równoległemu flushowi w normalnej pracy i pozwala odzyskać bufor po śmierci workera. W systemie o dużej
skali lista odbiorców kampanii i treści digestu powinna trafić do osobnego, partycjonowanego storage'u.

## Wykonywalna semantyka kanałów

`ChannelSemantics` oddziela reguły medium od SDK dostawcy:

- email podejmuje decyzję `ALLOW/THROTTLE/PAUSE` na podstawie hard bounce, complaint i DMARC alignment;
- SMS liczy segmenty GSM-7/UCS-2, ponieważ długość oraz alfabet bezpośrednio wpływają na koszt;
- push przechowuje token per urządzenie i nie pozwala staremu invalidation usunąć nowego tokenu po rotacji;
- in-app utrzymuje idempotentną projekcję unread odporną na replay eventów delivery/read.

Progi reputacyjne są założeniami dydaktycznymi, nie uniwersalną rekomendacją providera. Produkcyjny system powinien
wersjonować politykę, rozdzielać domeny/IP oraz reagować na progi właściwe dla rynku i klasy ruchu.

## Metryki zgodne z operacjami

Kod, dashboard i alerty używają tych samych nazw. Tagi są ograniczone do małych zbiorów (`provider`, `outcome`,
`priority`); `tenantId`, `userId`, `jobId` i provider message id nie mogą być tagami metryk.

| Metryka | Co odpowiada | Dlaczego jest ważna |
| --- | --- | --- |
| `notification_oldest_ready_job_age_seconds` | wiek najstarszego należnego joba | wykrywa naruszenie SLO lepiej niż sama liczba rekordów |
| `notification_outbox_pending` | liczba nieopublikowanych eventów | pokazuje przerwanie ścieżki acceptance → job |
| `notification_dlq_size` | aktualna liczba jobów w DLQ | określa wielkość pracy naprawczej |
| `notification_time_to_send_seconds` | histogram czasu od utworzenia joba do `SENT` | umożliwia p95/p99 bez błędu średniej |
| `notification_provider_attempts_total` | próby według providera i wyniku | ujawnia degradację zależności i retry storm |
| `notification_provider_unknown_total` | niejednoznaczne efekty | wymaga pilnej reconciliation, nie zwiększania retry |

## Strategia testów tego laboratorium

- testy domenowe dowodzą dozwolonych przejść, consentu, DST i agregacji;
- testy historii generują wiele sekwencji terminalnych callbacków i sprawdzają niezmiennik monotoniczności;
- contract tests sprawdzają deterministyczność idempotency key oraz klasyfikację granicy HTTP;
- test API pokazuje izolację tenantów i fail-closed control plane;
- profil `infrastructure-tests` uruchamia prawdziwy PostgreSQL 17 oraz dowodzi constraintów, `SKIP LOCKED`, lease'u,
  odzyskania po awarii i trwałości checkpointów.

Test modelowy nie dowodzi poprawności wszystkich możliwych przeplotów, ale bada historię operacji zamiast jednego
happy-path wyniku. Laboratorium ma również fault injection dla crash-after-effect oraz open-model load, spike i soak
w [load-tests](load-tests/README.md). Pełną granicą produkcyjną pozostają wielowęzłowe chaos tests i okresowy restore
drill z pomiarem RPO/RTO.

`ObservabilityContractTest` wykrywa rozjazd nazw między kodem, dashboardem i alertami oraz zabrania tenantów,
użytkowników i identyfikatorów operacji w tagach metryk.

## Uruchamianie

```powershell
# szybkie testy domeny, API i pipeline'u bez Dockera
.\mvnw.cmd clean verify

# constrainty i konkurencyjne claimowanie na PostgreSQL 17
.\mvnw.cmd -Pinfrastructure-tests verify

# uruchomienie aplikacji na trwałym rdzeniu PostgreSQL
$env:NOTIFICATION_DB_URL="jdbc:postgresql://localhost:5432/notifications"
$env:NOTIFICATION_DB_USER="notifications"
$env:NOTIFICATION_DB_PASSWORD="change-me"
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=postgres-runtime

# pipeline przez lokalny stub/provider HTTP
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=http-provider

# JWT: tenant_id i roles pochodzą z podpisanego tokenu
$env:NOTIFICATION_JWT_SECRET="replace-with-at-least-32-random-bytes"
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=secure-runtime

# open-model load / spike / soak (wymaga k6)
k6 run -e PROFILE=load -e RATE=50 load-tests/notification-pipeline.js
```

Profil `postgres-runtime` obejmuje `Notification`, outbox, joby z lease'em, próby dostawy, webhook inbox,
suppression list, kampanie i digesty. Template'y, preferencje oraz domyślny routing providerów pozostają celowymi
symulacjami dydaktycznymi; adapter HTTP jest testowany oddzielnie. Sekrety webhooka w repozytorium są wyłącznie
wartościami lokalnymi; poza laboratorium muszą pochodzić z secret managera i podlegać kontrolowanej rotacji.

## Szczegółowe kompendium teoretyczne

Poniższe rozdziały rozwijają decyzje systemowe, sizing, modele danych, kanały i scenariusze operacyjne. Pierwsza
część README jest mapą aktualnie wykonywalnych gwarancji; ta część stanowi materiał referencyjny do nauki i rozmów
system-design.

## 1. Cel systemu

System Notification ma umożliwiać niezawodne wysyłanie powiadomień do użytkowników przez wiele kanałów, np.:

- email,
- SMS,
- push mobile/web,
- in-app notifications,
- ewentualnie Slack/WhatsApp/webhook w przyszłości.

System powinien obsługiwać zarówno powiadomienia **transakcyjne**, jak i **marketingowe / produktowe**, przy czym te
pierwsze mają wyższy priorytet.

Przykłady:

- reset hasła,
- potwierdzenie płatności,
- alert bezpieczeństwa,
- przypomnienie o wydarzeniu,
- powiadomienie o nowej wiadomości,
- newsletter,
- kampania promocyjna,
- alert systemowy.

---

## 2. Wymagania funkcjonalne

### 2.1. Tworzenie powiadomień

System powinien pozwalać innym serwisom na tworzenie zleceń powiadomień przez API lub eventy.

Przykład:

```json
{
  "user_id": "user_123",
  "type": "PASSWORD_RESET",
  "channels": [
    "email"
  ],
  "priority": "high",
  "template_id": "password_reset_v2",
  "payload": {
    "reset_link": "https://example.com/reset/abc"
  }
}
```

Źródłami powiadomień mogą być:

- User Service,
- Payment Service,
- Order Service,
- Messaging Service,
- Marketing Platform,
- Admin Panel,
- Scheduler.

---

### 2.2. Obsługa wielu kanałów

System powinien wspierać różne kanały dostarczenia:

| Kanał   | Przykład providera                    |
|---------|---------------------------------------|
| Email   | SendGrid, SES, Mailgun                |
| SMS     | Twilio, Vonage                        |
| Push    | Firebase Cloud Messaging, APNs        |
| In-app  | własna baza + WebSocket/SSE           |
| Webhook | HTTP callback do zewnętrznego systemu |

Każdy kanał powinien mieć osobny adapter, żeby nie wiązać logiki biznesowej z konkretnym dostawcą.

---

### 2.3. Szablony wiadomości

System powinien obsługiwać szablony wiadomości.

Przykład szablonu email:

```html
Cześć {{first_name}},

Kliknij tutaj, aby zresetować hasło:
{{reset_link}}
```

Szablony powinny wspierać:

- wersjonowanie,
- różne języki,
- różne kanały,
- podgląd przed wysyłką,
- walidację wymaganych zmiennych,
- fallback językowy, np. `pl-PL -> pl -> en`.

Przykładowa struktura:

```text
template_id: password_reset
version: 3
locale: pl-PL
channel: email
subject: Reset hasła
body: ...
```

---

### 2.4. Preferencje użytkownika

Użytkownik powinien móc zdecydować, jakie typy powiadomień chce otrzymywać.

| Typ powiadomienia | Email | SMS | Push | In-app |
|-------------------|------:|----:|-----:|-------:|
| Security alerts   |   tak | tak |  tak |    tak |
| Marketing         |   nie | nie |  tak |    tak |
| Payment updates   |   tak | tak |  nie |    tak |
| Weekly summary    |   tak | nie |  nie |    tak |

Ważne: nie wszystkie powiadomienia powinny dać się wyłączyć. Przykładowo reset hasła, alert bezpieczeństwa albo
informacja prawna mogą być obowiązkowe.

---

### 2.5. Priorytety

System powinien wspierać priorytety:

| Priorytet | Przykład                               | SLA         |
|-----------|----------------------------------------|-------------|
| Critical  | OTP, reset hasła, alert bezpieczeństwa | sekundy     |
| High      | płatność, zamówienie                   | < 1 min     |
| Normal    | komentarz, wiadomość                   | kilka minut |
| Low       | newsletter, digest                     | best effort |

Priorytet wpływa na:

- kolejkę,
- liczbę retry,
- timeout,
- provider fallback,
- rate limit,
- monitoring.

---

### 2.6. Harmonogramowanie

System powinien wspierać:

- wysyłkę natychmiastową,
- wysyłkę o konkretnej godzinie,
- cykliczne kampanie,
- digesty dzienne/tygodniowe,
- powiadomienia z opóźnieniem.

Przykład:

```json
{
  "send_at": "2026-06-05T18:00:00Z"
}
```

---

### 2.7. Deduplicacja

System powinien zapobiegać przypadkowemu wysłaniu tego samego powiadomienia wiele razy.

Mechanizmy:

- `idempotency_key`,
- hash z `user_id + notification_type + entity_id`,
- deduplication window, np. 5 minut,
- unikalny constraint w bazie.

Przykład:

```text
idempotency_key = payment_failed:user_123:invoice_456
```

---

### 2.8. Statusy powiadomień

Każde powiadomienie powinno mieć cykl życia:

```text
CREATED
VALIDATED
QUEUED
PROCESSING
SENT
DELIVERED
FAILED
RETRYING
CANCELLED
EXPIRED
```

W praktyce trzeba rozróżnić:

- `SENT` — provider przyjął wiadomość,
- `DELIVERED` — wiadomość faktycznie dotarła,
- `OPENED` — użytkownik otworzył email/push,
- `CLICKED` — użytkownik kliknął link,
- `BOUNCED` — email wrócił,
- `UNSUBSCRIBED` — użytkownik się wypisał.

---

## 3. Wymagania niefunkcjonalne

### 3.1. Skalowalność

System powinien obsługiwać zarówno mały ruch, jak i duże kampanie.

Przykładowe założenia:

```text
10 mln użytkowników
1 mln aktywnych dziennie
średnio 5 powiadomień / użytkownika / dzień
peak: 50 000 notification requests / minutę
```

Nie projektowałbym tego jako jednego synchronicznego serwisu, bo wysyłka przez zewnętrznych providerów jest wolna,
zawodna i ma limity.

---

### 3.2. Dostępność

Cel:

```text
API availability: 99.9%+
Delivery pipeline availability: 99.5%+
```

System powinien przyjmować zlecenia nawet wtedy, gdy provider email/SMS ma awarię. Wtedy powiadomienia trafiają do
kolejki retry albo są kierowane do alternatywnego providera.

---

### 3.3. Niezawodność

Ważniejsze jest, żeby powiadomienie nie zginęło, niż żeby zawsze zostało wysłane natychmiast.

Mechanizmy:

- durable queue,
- retry z backoffem,
- dead-letter queue,
- idempotency,
- audyt statusów,
- outbox pattern,
- provider fallback.

---

### 3.4. Latencja

Dla powiadomień krytycznych:

```text
p95 enqueue latency < 100 ms
p95 processing start < 1 s
p95 provider accepted < 5 s
```

Dla newsletterów czy kampanii marketingowych latencja może być znacznie większa.

---

### 3.5. Spójność

System może być **eventually consistent**.

Nie ma potrzeby, żeby status `DELIVERED` był natychmiast znany każdemu serwisowi. Wystarczy, że:

- zlecenie zostało trwale zapisane,
- system podejmie próbę wysłania,
- status zostanie później zaktualizowany.

---

## 4. High-Level Architecture

```text
                        ┌────────────────────┐
                        │  Client Services   │
                        │ User/Order/Payment │
                        └─────────┬──────────┘
                                  │
                                  ▼
                        ┌────────────────────┐
                        │ Notification API   │
                        └─────────┬──────────┘
                                  │
                                  ▼
                        ┌────────────────────┐
                        │ Notification DB    │
                        └─────────┬──────────┘
                                  │
                                  ▼
                        ┌────────────────────┐
                        │ Message Broker     │
                        │ Kafka/SQS/RabbitMQ │
                        └──────┬──────┬──────┘
                               │      │
              ┌────────────────┘      └────────────────┐
              ▼                                        ▼
     ┌──────────────────┐                    ┌──────────────────┐
     │ Email Worker     │                    │ SMS Worker       │
     └────────┬─────────┘                    └────────┬─────────┘
              │                                       │
              ▼                                       ▼
     ┌──────────────────┐                    ┌──────────────────┐
     │ Email Provider   │                    │ SMS Provider     │
     └──────────────────┘                    └──────────────────┘

              ┌──────────────────┐
              │ Push Worker      │
              └────────┬─────────┘
                       ▼
              ┌──────────────────┐
              │ FCM / APNs       │
              └──────────────────┘

              ┌──────────────────┐
              │ In-App Service   │
              └──────────────────┘
```

---

## 5. Główne komponenty

### 5.1. Notification API

Odpowiada za przyjmowanie zleceń.

Zadania:

- autoryzacja serwisów,
- walidacja requestu,
- sprawdzenie idempotency key,
- zapis notification requestu,
- publikacja eventu do brokera,
- zwrócenie `notification_id`.

API nie powinno synchronicznie wysyłać wiadomości do providera.

Przykładowa odpowiedź:

```json
{
  "notification_id": "notif_789",
  "status": "QUEUED"
}
```

---

### 5.2. Notification Orchestrator

To centralny komponent decydujący, co właściwie należy wysłać.

Zadania:

- pobranie preferencji użytkownika,
- wybór kanałów,
- sprawdzenie reguł biznesowych,
- dobranie template’u,
- wykonanie personalizacji,
- rozbicie jednego requestu na wiele channel-specific jobs.

Przykład:

```text
Notification Request:
  user_id = 123
  type = PAYMENT_FAILED
  channels = auto

Orchestrator:
  sprawdza preferencje
  email = allowed
  sms = allowed for high priority
  push = disabled

Tworzy:
  email_job
  sms_job
```

---

### 5.3. Template Service

Odpowiada za szablony.

Funkcje:

- CRUD szablonów,
- wersjonowanie,
- rendering,
- walidacja zmiennych,
- fallback językowy,
- obsługa A/B testów,
- preview.

Szablony powinny być cache’owane, bo będą często używane.

Rekomendacja:

- baza: PostgreSQL,
- cache: Redis,
- rendering: Handlebars, Mustache, Liquid albo własny ograniczony silnik.

Nie pozwalałbym na wykonywanie dowolnego kodu w szablonach. To ryzyko bezpieczeństwa.

---

### 5.4. Preference Service

Przechowuje ustawienia użytkownika.

Przykładowy model:

```json
{
  "user_id": "user_123",
  "preferences": {
    "marketing": {
      "email": false,
      "sms": false,
      "push": true
    },
    "security": {
      "email": true,
      "sms": true,
      "push": true,
      "mandatory": true
    }
  }
}
```

Powinien obsługiwać:

- global opt-out,
- opt-out per kanał,
- opt-out per kategoria,
- wymogi prawne,
- unsubscribe links,
- double opt-in dla marketingu,
- historia zmian preferencji.

---

### 5.5. Queue / Message Broker

Broker jest krytyczny.

Możliwe opcje:

| Technologia       | Dobre zastosowanie                 |
|-------------------|------------------------------------|
| Kafka             | duża skala, event streaming, audyt |
| SQS               | prostota, managed queue, AWS       |
| RabbitMQ          | routing, mniejsze/średnie systemy  |
| Google Pub/Sub    | GCP                                |
| Azure Service Bus | Azure                              |

Dla dużego systemu wybrałbym:

```text
Kafka albo SQS + DLQ
```

Przykładowe topiki/kolejki:

```text
notification.created
notification.email.high
notification.email.normal
notification.sms.high
notification.push.normal
notification.retry
notification.dlq
notification.status.updated
```

Oddzielne kolejki per kanał i priorytet ułatwiają skalowanie.

---

### 5.6. Workers

Workers wykonują właściwą wysyłkę.

Każdy kanał ma osobny worker:

- Email Worker,
- SMS Worker,
- Push Worker,
- In-App Worker,
- Webhook Worker.

Zadania workera:

1. pobiera job z kolejki,
2. sprawdza status i idempotency,
3. renderuje wiadomość albo pobiera wyrenderowaną,
4. wywołuje provider adapter,
5. zapisuje wynik,
6. w razie błędu planuje retry,
7. po permanentnym błędzie wysyła do DLQ.

---

### 5.7. Provider Adapter Layer

Nie należy wołać SendGrid, Twilio czy FCM bezpośrednio z logiki biznesowej.

Lepszy wzorzec:

```text
EmailWorker -> EmailProviderInterface -> SendGridAdapter
                                      -> SESAdapter
                                      -> MailgunAdapter
```

Interfejs:

```typescript
interface EmailProvider {
  sendEmail(message: EmailMessage): Promise<ProviderResponse>;
}
```

Dzięki temu można:

- zmienić providera,
- zrobić fallback,
- testować lokalnie,
- mierzyć jakość providerów,
- ograniczyć vendor lock-in.

---

### 5.8. Delivery Status Webhook Handler

Providerzy często wysyłają webhooks o statusie dostarczenia.

Przykład:

- email delivered,
- email bounced,
- SMS delivered,
- push token invalid,
- user unsubscribed,
- spam complaint.

Webhook Handler powinien:

- zweryfikować podpis providera,
- zmapować status providera na wewnętrzny status,
- zaktualizować bazę,
- opublikować event `notification.status.updated`.

---

## 6. Proponowany przepływ danych

### 6.1. Wysyłka natychmiastowa

```text
1. Payment Service wykrywa failed payment.
2. Payment Service wywołuje Notification API.
3. API waliduje request.
4. API zapisuje NotificationRequest w DB.
5. API publikuje notification.created.
6. Orchestrator pobiera event.
7. Orchestrator sprawdza preferencje i szablon.
8. Orchestrator tworzy email_job oraz push_job.
9. Jobs trafiają do kolejek kanałowych.
10. Email Worker wysyła email.
11. Push Worker wysyła push.
12. Statusy są zapisywane w DB.
13. Provider webhook aktualizuje status DELIVERED/BOUNCED.
```

---

### 6.2. Wysyłka zaplanowana

```text
1. Service tworzy notification z send_at.
2. Notification API zapisuje request jako SCHEDULED.
3. Scheduler cyklicznie szuka rekordów gotowych do wysyłki.
4. Scheduler publikuje notification.created.
5. Dalej przepływ jest taki sam jak dla wysyłki natychmiastowej.
```

Dla dużej skali scheduler nie powinien robić prostego `SELECT * WHERE send_at <= now()`, bo to może zabić bazę. Lepiej
użyć indeksów, shardingu czasowego albo opóźnionych kolejek, jeśli technologia to wspiera.

---

### 6.3. In-app notification

In-app notification różni się od email/SMS, bo musi być przechowywane i widoczne w aplikacji.

Przepływ:

```text
1. InAppWorker zapisuje notification do tabeli user_notifications.
2. Jeśli użytkownik jest online, wysyła event przez WebSocket/SSE.
3. Frontend pokazuje badge/licznik.
4. Użytkownik może oznaczyć jako przeczytane.
```

---

## 7. API Design

### 7.1. Create Notification

```http
POST /v1/notifications
```

Request:

```json
{
  "user_id": "user_123",
  "type": "PAYMENT_FAILED",
  "priority": "high",
  "channels": [
    "email",
    "push"
  ],
  "template_id": "payment_failed_v1",
  "locale": "pl-PL",
  "payload": {
    "amount": "99.00 PLN",
    "invoice_url": "https://example.com/invoices/456"
  },
  "idempotency_key": "payment_failed:user_123:invoice_456"
}
```

Response:

```json
{
  "notification_id": "notif_789",
  "status": "QUEUED"
}
```

---

### 7.2. Get Notification Status

```http
GET /v1/notifications/{notification_id}
```

Response:

```json
{
  "notification_id": "notif_789",
  "status": "SENT",
  "channels": [
    {
      "channel": "email",
      "status": "DELIVERED",
      "provider": "sendgrid",
      "sent_at": "2026-06-05T12:00:00Z",
      "delivered_at": "2026-06-05T12:00:03Z"
    },
    {
      "channel": "push",
      "status": "FAILED",
      "error_code": "INVALID_TOKEN"
    }
  ]
}
```

---

### 7.3. Cancel Scheduled Notification

```http
DELETE /v1/notifications/{notification_id}
```

Dozwolone tylko dla statusów:

```text
SCHEDULED
QUEUED
```

Nie powinno się anulować wiadomości, która już została wysłana do providera.

---

### 7.4. User Preferences

```http
GET /v1/users/{user_id}/notification-preferences
PUT /v1/users/{user_id}/notification-preferences
```

---

### 7.5. In-app Notifications

```http
GET /v1/users/{user_id}/notifications
PATCH /v1/users/{user_id}/notifications/{id}/read
PATCH /v1/users/{user_id}/notifications/read-all
```

---

## 8. Model danych

### 8.1. `notification_requests`

```sql
CREATE TABLE notification_requests
(
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL,
    type            VARCHAR(100) NOT NULL,
    priority        VARCHAR(20)  NOT NULL,
    status          VARCHAR(30)  NOT NULL,
    template_id     VARCHAR(100),
    locale          VARCHAR(20),
    payload         JSONB        NOT NULL,
    idempotency_key VARCHAR(255),
    scheduled_at    TIMESTAMP NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,

    UNIQUE (idempotency_key)
);
```

Indeksy:

```sql
CREATE INDEX idx_notification_requests_user_id
    ON notification_requests (user_id);

CREATE INDEX idx_notification_requests_status_scheduled
    ON notification_requests (status, scheduled_at);

CREATE INDEX idx_notification_requests_created_at
    ON notification_requests (created_at);
```

---

### 8.2. `notification_jobs`

```sql
CREATE TABLE notification_jobs
(
    id                      UUID PRIMARY KEY,
    notification_request_id UUID        NOT NULL,
    user_id                 UUID        NOT NULL,
    channel                 VARCHAR(30) NOT NULL,
    status                  VARCHAR(30) NOT NULL,
    provider                VARCHAR(50),
    provider_message_id     VARCHAR(255),
    attempt_count           INT         NOT NULL DEFAULT 0,
    max_attempts            INT         NOT NULL DEFAULT 5,
    next_retry_at           TIMESTAMP NULL,
    error_code              VARCHAR(100),
    error_message           TEXT,
    created_at              TIMESTAMP   NOT NULL,
    updated_at              TIMESTAMP   NOT NULL
);
```

Indeksy:

```sql
CREATE INDEX idx_notification_jobs_status_retry
    ON notification_jobs (status, next_retry_at);

CREATE INDEX idx_notification_jobs_request_id
    ON notification_jobs (notification_request_id);

CREATE INDEX idx_notification_jobs_provider_message_id
    ON notification_jobs (provider_message_id);
```

---

### 8.3. `notification_templates`

```sql
CREATE TABLE notification_templates
(
    id                 UUID PRIMARY KEY,
    template_key       VARCHAR(100) NOT NULL,
    channel            VARCHAR(30)  NOT NULL,
    locale             VARCHAR(20)  NOT NULL,
    version            INT          NOT NULL,
    subject            TEXT,
    body               TEXT         NOT NULL,
    required_variables JSONB,
    is_active          BOOLEAN      NOT NULL DEFAULT true,
    created_at         TIMESTAMP    NOT NULL,
    updated_at         TIMESTAMP    NOT NULL,

    UNIQUE (template_key, channel, locale, version)
);
```

---

### 8.4. `notification_preferences`

```sql
CREATE TABLE notification_preferences
(
    user_id           UUID         NOT NULL,
    notification_type VARCHAR(100) NOT NULL,
    channel           VARCHAR(30)  NOT NULL,
    enabled           BOOLEAN      NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,

    PRIMARY KEY (user_id, notification_type, channel)
);
```

---

### 8.5. `in_app_notifications`

```sql
CREATE TABLE in_app_notifications
(
    id                      UUID PRIMARY KEY,
    user_id                 UUID      NOT NULL,
    notification_request_id UUID,
    title                   TEXT      NOT NULL,
    body                    TEXT      NOT NULL,
    action_url              TEXT,
    read_at                 TIMESTAMP NULL,
    created_at              TIMESTAMP NOT NULL
);
```

Indeksy:

```sql
CREATE INDEX idx_in_app_user_created
    ON in_app_notifications (user_id, created_at DESC);

CREATE INDEX idx_in_app_user_unread
    ON in_app_notifications (user_id, read_at) WHERE read_at IS NULL;
```

---

### 8.6. `notification_events`

Tabela audytowa.

```sql
CREATE TABLE notification_events
(
    id                      UUID PRIMARY KEY,
    notification_job_id     UUID,
    notification_request_id UUID,
    event_type              VARCHAR(100) NOT NULL,
    metadata                JSONB,
    created_at              TIMESTAMP    NOT NULL
);
```

Przykładowe eventy:

```text
REQUEST_CREATED
JOB_CREATED
JOB_SENT
JOB_DELIVERED
JOB_FAILED
JOB_RETRIED
JOB_EXPIRED
PROVIDER_WEBHOOK_RECEIVED
```

---

### 8.7. `delivery_attempts`, `webhook_inbox` i `suppression_entries`

`delivery_attempts` jest append-only historią efektów na granicy providera. Unikalność `(job_id, attempt_number)`
zapobiega nadpisaniu jednej próby drugą. `outcome` przechowuje `ACCEPTED`, `NOT_SENT`, `UNKNOWN` lub `REJECTED`;
nie wolno rekonstruować tej informacji wyłącznie z końcowego statusu joba.

`webhook_inbox` przechowuje identyfikator eventu providera, status, chwilę przyjęcia, wynik przetworzenia i błąd.
HTTP może potwierdzić duplikat tego samego eventu bez ponownego wykonania przejścia domenowego. Inbox powinien być
retencjonowany co najmniej tak długo, jak provider może ponawiać callback.

`suppression_entries` używa hasha contact pointu zamiast surowego adresu. Klucz `(tenant, channel, contact_hash)`
pozwala zablokować kolejną wysyłkę po hard bounce, complaint lub invalid token. Hash ogranicza ekspozycję PII, ale
nie zastępuje kontroli dostępu — przestrzeń numerów telefonu i popularnych adresów może być podatna na enumerację,
więc produkcyjnie warto zastosować HMAC z sekretem zamiast niesolonego SHA-256.

## 9. Kolejki i topiki

Proponowany podział:

```text
notification.created

notification.email.critical
notification.email.high
notification.email.normal
notification.email.low

notification.sms.critical
notification.sms.high
notification.sms.normal

notification.push.critical
notification.push.high
notification.push.normal
notification.push.low

notification.inapp.normal

notification.retry
notification.dlq
notification.status.updated
```

Dlaczego nie jedna kolejka?

Bo różne kanały mają różne:

- limity,
- czasy odpowiedzi,
- koszty,
- strategie retry,
- priorytety,
- providerów.

SMS jest droższy niż email, a push ma inne błędy niż email. Wrzucenie wszystkiego do jednej kolejki utrudnia kontrolę.

---

## 10. Retry strategy

Retry trzeba projektować ostrożnie, bo można przypadkowo zasypać providera albo użytkownika.

### 10.1. Błędy tymczasowe

Przykłady:

- timeout,
- 429 rate limited,
- 500 provider error,
- network error.

Strategia:

```text
retry with exponential backoff + jitter
```

Przykład:

```text
1. retry: po 30 sekundach
2. retry: po 2 minutach
3. retry: po 10 minutach
4. retry: po 30 minutach
5. retry: po 2 godzinach
```

---

### 10.2. Błędy permanentne

Przykłady:

- invalid email,
- invalid phone number,
- push token expired,
- user unsubscribed,
- hard bounce.

Nie robimy retry. Aktualizujemy status i ewentualnie profil użytkownika.

---

### 10.3. Dead-letter queue

Do DLQ trafiają wiadomości, których nie udało się przetworzyć po maksymalnej liczbie prób.

DLQ powinno mieć:

- dashboard,
- alerty,
- możliwość replay,
- możliwość ręcznego oznaczenia jako ignored,
- zapis błędu.

---

## 11. Rate limiting i throttling

System musi uwzględniać limity providerów.

Przykład:

```text
SendGrid: X wiadomości / sekundę
Twilio: Y SMS / sekundę
FCM: Z requestów / sekundę
```

Potrzebne mechanizmy:

- global rate limit per provider,
- rate limit per tenant,
- rate limit per user,
- rate limit per notification type,
- throttling kampanii marketingowych,
- oddzielna przepustowość dla powiadomień krytycznych.

Przykład polityki:

```text
critical/security: omija limity marketingowe
marketing: max 1 email / user / dzień
sms: max 3 SMS / user / dzień
push: max 10 push / user / dzień
```

---

## 12. Idempotency

Każdy endpoint tworzący powiadomienie powinien wspierać `idempotency_key`.

Scenariusz:

```text
Payment Service wysyła request.
Notification API zapisuje notification.
Payment Service nie dostaje odpowiedzi przez timeout.
Payment Service ponawia request.
Bez idempotency użytkownik dostaje dwa emaile.
```

Rozwiązanie:

```text
UNIQUE(idempotency_key)
```

Jeżeli request z takim kluczem już istnieje, API zwraca istniejące `notification_id`.

---

## 13. Outbox Pattern

Jeżeli Notification API zapisuje request do bazy i publikuje event do brokera, może wystąpić problem:

```text
DB write success
Broker publish failed
```

Wtedy request istnieje, ale nikt go nie przetworzy.

Lepszy wzorzec:

```text
1. W jednej transakcji zapisujemy notification_request i outbox_event.
2. Osobny Outbox Publisher czyta outbox_events.
3. Publikuje event do brokera.
4. Oznacza outbox_event jako published.
```

Tabela:

```sql
CREATE TABLE outbox_events
(
    id           UUID PRIMARY KEY,
    aggregate_id UUID         NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      JSONB        NOT NULL,
    status       VARCHAR(30)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    published_at TIMESTAMP NULL
);
```

To jest ważny element. Bez niego system może wyglądać poprawnie, ale gubić powiadomienia w rzadkich edge case’ach.

---

## 14. Provider fallback

Dla krytycznych powiadomień warto mieć fallback.

Przykład email:

```text
Primary: SendGrid
Fallback: Amazon SES
```

Przykład SMS:

```text
Primary: Twilio
Fallback: Vonage
```

Strategia:

```text
1. Spróbuj primary provider.
2. Jeśli timeout/5xx/429 utrzymuje się, przełącz na fallback.
3. Nie przełączaj przy błędach permanentnych, np. invalid phone.
```

Trzeba uważać, żeby fallback nie wysłał duplikatu, jeśli primary faktycznie wysłał wiadomość, ale odpowiedź zaginęła.
Dlatego provider-level idempotency, jeśli dostępne, jest bardzo przydatne.

---

## 15. Observability

### 15.1. Metryki

Najważniejsze metryki:

```text
notifications_created_total
notifications_sent_total
notifications_failed_total
notifications_delivered_total
notification_processing_latency
notification_queue_lag
provider_latency
provider_error_rate
retry_count
dlq_size
bounce_rate
unsubscribe_rate
sms_cost_total
push_invalid_token_rate
```

---

### 15.2. Logi

Każdy request powinien mieć:

```text
correlation_id
notification_id
job_id
user_id
channel
provider
status
error_code
```

Nie logowałbym pełnej treści wiadomości, szczególnie jeśli może zawierać dane osobowe, linki resetu hasła albo kody OTP.

---

### 15.3. Tracing

Distributed tracing:

```text
Payment Service
  -> Notification API
    -> Outbox Publisher
      -> Kafka
        -> Email Worker
          -> Provider
```

Trace ID powinien przechodzić przez cały pipeline.

---

### 15.4. Alerty

Przykładowe alerty:

```text
DLQ size > threshold
provider error rate > 5% przez 5 minut
critical notification p95 latency > 10s
queue lag rośnie przez 10 minut
bounce rate spike
webhook signature validation failures
```

---

## 16. Bezpieczeństwo

### 16.1. Autoryzacja

Notification API nie powinno być publiczne dla każdego klienta.

Opcje:

- service-to-service auth,
- mTLS,
- OAuth client credentials,
- signed requests,
- API keys dla zewnętrznych tenantów.

---

### 16.2. Ochrona danych

Powiadomienia mogą zawierać dane wrażliwe.

Zasady:

- szyfrowanie danych w spoczynku,
- TLS w komunikacji,
- nie logować payloadów z PII,
- maskowanie emaili i telefonów w logach,
- krótki TTL dla OTP/reset tokenów,
- ograniczony dostęp do tabel audytowych.

---

### 16.3. Template injection

Jeśli użytkownik albo admin może edytować template, trzeba pilnować:

- braku wykonywania kodu,
- escapingu HTML,
- walidacji zmiennych,
- ograniczenia helperów,
- osobnej roli do publikowania template’ów.

---

### 16.4. Unsubscribe i compliance

Dla marketingu:

- link unsubscribe,
- suppression list,
- historia zgód,
- double opt-in tam, gdzie wymagane,
- brak wysyłki marketingowej bez zgody.

Dla powiadomień transakcyjnych unsubscribe nie zawsze ma zastosowanie.

---

## 17. Multi-tenancy

Jeżeli system obsługuje wiele organizacji/tenantów, prawie wszystkie tabele powinny mieć `tenant_id`.

Przykład:

```sql
tenant_id
UUID NOT NULL
```

Wtedy rate limits, providery, template’y i preferencje mogą być tenant-specific.

Trzeba pilnować izolacji:

- indeksy po `tenant_id`,
- autoryzacja per tenant,
- osobne limity,
- osobne klucze providerów,
- opcjonalnie osobne kolejki dla dużych tenantów.

---

## 18. Sharding i skalowanie bazy

Przy dużej skali tabele `notification_jobs` i `notification_events` będą rosły bardzo szybko.

### 18.1. Partycjonowanie po czasie

```text
notification_events_2026_06
notification_events_2026_07
...
```

Dobre dla audytu i archiwizacji.

### 18.2. Partycjonowanie po user_id

Dobre dla zapytań użytkownika, np. in-app notifications.

### 18.3. TTL / archiwizacja

Przykład:

```text
notification_jobs: trzymamy 90 dni
notification_events: trzymamy 180 dni
in_app_notifications: trzymamy 1 rok albo według ustawień
```

Starsze dane można przenieść do object storage, np. S3.

---

## 19. Cache

Cache przyda się dla:

- template’ów,
- preferencji użytkownika,
- konfiguracji providerów,
- rate limitów,
- suppression list.

Redis:

```text
template:{template_key}:{channel}:{locale}:{version}
preferences:{user_id}
rate_limit:{provider}:{window}
dedupe:{idempotency_key}
```

Trzeba uważać, żeby cache preferencji nie spowodował wysłania wiadomości po unsubscribe. Dla operacji opt-out warto
natychmiast invalidować cache.

---

## 20. Handling user identity and contact data

Notification System nie musi być właścicielem danych użytkownika.

Możliwe podejścia:

### Opcja A — system pobiera dane z User Service

```text
Notification Worker -> User Service -> email/phone/push tokens
```

Plusy:

- jedno źródło prawdy.

Minusy:

- zależność runtime,
- większa latencja,
- User Service może stać się bottleneckiem.

### Opcja B — lokalna kopia contact points

Notification System przechowuje:

```text
user_id
email
phone_number
push_tokens
locale
timezone
```

Aktualizowana eventami z User Service.

Plusy:

- szybciej,
- odporniej.

Minusy:

- eventual consistency,
- trzeba dbać o synchronizację.

Dla dużego systemu wybrałbym opcję B z eventami:

```text
user.email.updated
user.phone.updated
user.push_token.added
user.push_token.removed
```

---

## 21. Push notifications

Push wymaga dodatkowej logiki.

Tabela tokenów:

```sql
CREATE TABLE push_tokens
(
    id           UUID PRIMARY KEY,
    user_id      UUID        NOT NULL,
    platform     VARCHAR(20) NOT NULL,
    token        TEXT        NOT NULL,
    status       VARCHAR(20) NOT NULL,
    last_seen_at TIMESTAMP,
    created_at   TIMESTAMP   NOT NULL,

    UNIQUE (token)
);
```

Jeżeli FCM/APNs zwróci invalid token, trzeba go oznaczyć jako nieaktywny.

```text
INVALID_TOKEN -> deactivate push token
```

Nie należy robić retry na niepoprawny token.

---

## 22. Email-specific concerns

Email wymaga obsługi:

- bounce,
- spam complaint,
- unsubscribe,
- open tracking,
- click tracking,
- SPF/DKIM/DMARC,
- suppression list,
- reputacji domeny.

System powinien mieć osobne domeny/nadawców dla różnych typów emaili:

```text
security.example.com
billing.example.com
marketing.example.com
```

Mieszanie krytycznych maili z marketingiem może zaszkodzić deliverability.

---

## 23. SMS-specific concerns

SMS jest drogi, więc potrzebne są limity.

Dodatkowo:

- walidacja numeru telefonu,
- format E.164,
- country-specific rules,
- limity per kraj,
- fallback kanału, np. jeśli SMS za drogi, użyj push dla non-critical,
- ochrona przed spamem OTP.

Przykład E.164:

```text
+48123123123
```

---

## 24. In-app notification design

### 24.1. REST API

```http
GET /v1/in-app-notifications?limit=20&cursor=abc
```

Response:

```json
{
  "items": [
    {
      "id": "inapp_123",
      "title": "Nowa wiadomość",
      "body": "Masz nową wiadomość od Anny",
      "action_url": "/messages/456",
      "read_at": null,
      "created_at": "2026-06-05T10:00:00Z"
    }
  ],
  "next_cursor": "def"
}
```

### 24.2. Real-time

Opcje:

- WebSocket,
- Server-Sent Events,
- polling.

Dla większości produktów wystarczy:

```text
WebSocket dla aktywnych użytkowników
REST API jako source of truth
```

WebSocket nie powinien być jedynym źródłem, bo użytkownik może być offline.

---

## 25. Exactly-once vs at-least-once

W praktyce system powinien zakładać model:

```text
at-least-once processing
```

Czyli worker może dostać tę samą wiadomość więcej niż raz.

Dlatego potrzebujemy:

- idempotency,
- deduplikacji,
- statusów jobów,
- provider message id,
- blokady przetwarzania.

Nie obiecywałbym exactly-once end-to-end, bo zewnętrzni providerzy i sieć tego nie gwarantują.

---

## 26. Concurrency control

Worker może równolegle przetwarzać ten sam job, jeśli retry albo redelivery z kolejki zadziałają w złym momencie.

Rozwiązania:

- optimistic locking,
- status transition z warunkiem,
- distributed lock,
- `SELECT FOR UPDATE SKIP LOCKED`,
- idempotent provider calls.

Przykład:

```sql
UPDATE notification_jobs
SET status = 'PROCESSING'
WHERE id = :job_id
  AND status IN ('QUEUED', 'RETRYING');
```

Jeśli affected rows = 0, worker nie powinien przetwarzać joba.

---

## 27. Status transition model

Dobrze zdefiniować dozwolone przejścia.

```text
CREATED -> VALIDATED
VALIDATED -> QUEUED
QUEUED -> PROCESSING
PROCESSING -> SENT
PROCESSING -> FAILED
FAILED -> RETRYING
RETRYING -> PROCESSING
FAILED -> DLQ
SENT -> DELIVERED
SENT -> BOUNCED
SENT -> OPENED
SENT -> CLICKED
```

Niektóre statusy są końcowe:

```text
DELIVERED
BOUNCED
CANCELLED
EXPIRED
DLQ
```

---

## 28. Failure scenarios

### 28.1. Provider niedostępny

```text
Worker dostaje timeout.
Job przechodzi do RETRYING.
Po kilku błędach high-priority job przechodzi na fallback provider.
```

### 28.2. Broker niedostępny

Notification API nie powinno publikować bezpośrednio do brokera bez outboxa.

```text
Request zapisany w DB + outbox.
Outbox Publisher opublikuje później.
```

### 28.3. DB chwilowo niedostępna

API powinno zwrócić błąd, bo bez trwałego zapisu nie można obiecać wysyłki.

Nie należy przyjmować powiadomienia tylko do pamięci.

### 28.4. Webhook od providera przychodzi przed aktualizacją joba

Może się zdarzyć.

Rozwiązanie:

- szukamy po `provider_message_id`,
- jeśli nie ma, zapisujemy webhook jako pending event,
- później reconciler dopasowuje.

### 28.5. Użytkownik wypisał się po zakolejkowaniu wiadomości

Dla marketingu worker powinien sprawdzić preferencje tuż przed wysłaniem albo mieć krótki TTL cache. Dla krytycznych
powiadomień obowiązują inne reguły.

---

## 29. Reconciliation jobs

Warto mieć okresowe joby naprawcze:

```text
find stuck PROCESSING jobs older than 15 min
find SENT jobs without provider callback after 24h
replay failed outbox events
sync provider suppression lists
clean invalid push tokens
archive old notification events
```

Bez takich jobów system będzie z czasem gromadził „zombie statusy”.

---

## 30. Cost control

Koszty będą szczególnie ważne dla SMS i email marketingu.

Mechanizmy:

- budżet per tenant,
- budżet per kanał,
- rate limit SMS,
- limity kampanii,
- wybór tańszego kanału dla low-priority,
- agregowanie powiadomień w digest,
- deduplikacja podobnych alertów.

Przykład:

```text
Jeśli użytkownik dostaje 20 komentarzy w 5 minut,
nie wysyłaj 20 emaili.
Wyślij jeden digest: "Masz 20 nowych komentarzy".
```

---

## 31. Aggregation / Digest

Dla powiadomień niskiego priorytetu warto wspierać agregację.

Przykład:

```text
Zamiast:
- Anna skomentowała
- Piotr skomentował
- Maria skomentowała

Wyślij:
- Masz 3 nowe komentarze
```

Można to zrobić przez:

- agregację per user + notification type + time window,
- np. 5 minut dla komentarzy,
- 24h dla daily digest.

Tabela:

```sql
CREATE TABLE notification_digest_buffer
(
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL,
    digest_key VARCHAR(255) NOT NULL,
    items      JSONB        NOT NULL,
    flush_at   TIMESTAMP    NOT NULL,
    status     VARCHAR(30)  NOT NULL
);
```

---

## 32. Template versioning

Nie należy nadpisywać aktywnego szablonu bez wersjonowania.

Problem:

```text
Notification utworzony o 10:00 używa template v1.
Admin edytuje template o 10:01.
Worker wysyła o 10:05.
Który template powinien zostać użyty?
```

Najbezpieczniej:

- notification request zapisuje `template_version`,
- worker renderuje konkretną wersję,
- stare wersje są immutable.

---

## 33. Localization and timezone

System powinien znać:

- `locale`,
- `timezone`,
- format daty,
- format waluty,
- fallback językowy.

Przykład:

```text
user.locale = pl-PL
user.timezone = Europe/Warsaw
```

Dla kampanii:

```text
send at 9:00 local user time
```

To oznacza, że scheduler musi umieć rozbić kampanię na wiele stref czasowych.

---

## 34. Suggested technology stack

Jedna możliwa konfiguracja:

```text
API: Java/Kotlin Spring Boot, Go albo Node.js
DB: PostgreSQL
Queue: Kafka albo AWS SQS
Cache: Redis
Workers: Go/Java/Kotlin
Search/analytics: ClickHouse albo BigQuery
Object storage: S3
Monitoring: Prometheus + Grafana
Tracing: OpenTelemetry
Logs: Loki/ELK
Email: SES + SendGrid fallback
SMS: Twilio + Vonage fallback
Push: FCM/APNs
```

Dla prostszego MVP:

```text
API: Node.js/NestJS lub Spring Boot
DB: PostgreSQL
Queue: SQS/RabbitMQ
Cache: Redis
Email: SendGrid/SES
SMS: Twilio
Push: FCM
```

---

## 35. MVP scope

Nie budowałbym wszystkiego od razu. Sensowny MVP:

### MVP

- Notification API,
- Email channel,
- Push channel,
- proste template’y,
- PostgreSQL,
- kolejka,
- retry,
- DLQ,
- idempotency,
- podstawowe preferencje,
- statusy,
- dashboard techniczny.

### Później

- SMS,
- provider fallback,
- A/B testing,
- digest,
- kampanie marketingowe,
- zaawansowany scheduler,
- multi-tenancy,
- analytics,
- cost control,
- suppression lists,
- in-app real-time.

---

## 36. Trade-offy

### 36.1. Synchroniczna wysyłka vs asynchroniczna

**Synchroniczna** jest prostsza, ale zawodna i wolna.

**Asynchroniczna** jest bardziej złożona, ale lepiej skaluje się i izoluje system od awarii providerów.

Rekomendacja: **asynchroniczna**.

---

### 36.2. Jeden worker vs osobne workery per kanał

Jeden worker jest prostszy, ale trudniejszy do skalowania i debugowania.

Osobne workery dają lepszą kontrolę.

Rekomendacja: **osobne workery per kanał**.

---

### 36.3. Jedna kolejka vs wiele kolejek

Jedna kolejka jest prostsza.

Wiele kolejek pozwala odseparować priorytety i kanały.

Rekomendacja: **wiele kolejek dla większego systemu**, jedna kolejka tylko dla MVP.

---

### 36.4. Pobieranie danych użytkownika live vs lokalna kopia

Live lookup jest spójniejszy, ale wolniejszy i mniej odporny.

Lokalna kopia jest szybsza, ale eventual consistent.

Rekomendacja: dla większej skali **lokalna kopia contact points aktualizowana eventami**.

---

## 37. Najważniejsze ryzyka

Największe ryzyka w takim systemie:

1. **Duplikaty powiadomień**  
   Rozwiązanie: idempotency, dedupe, status locking.

2. **Zgubione powiadomienia**  
   Rozwiązanie: durable queue, outbox pattern, retry.

3. **Provider outage**  
   Rozwiązanie: fallback, retry, circuit breaker.

4. **Wysyłka mimo opt-out**  
   Rozwiązanie: preference check blisko momentu wysyłki, invalidacja cache.

5. **Koszty SMS**  
   Rozwiązanie: rate limits, budżety, fallback do push/email.

6. **Kampanie blokujące powiadomienia krytyczne**  
   Rozwiązanie: osobne kolejki i priorytety.

7. **Niewłaściwe logowanie PII**  
   Rozwiązanie: maskowanie, ograniczenie logów, szyfrowanie.

---

## 38. Proponowany docelowy design

Najrozsądniejsza architektura produkcyjna:

```text
Notification API
  -> PostgreSQL
  -> Outbox Events
  -> Outbox Publisher
  -> Kafka/SQS

Kafka/SQS
  -> Notification Orchestrator
  -> Channel Queues

Channel Queues
  -> Email Worker
  -> SMS Worker
  -> Push Worker
  -> In-App Worker

Workers
  -> Provider Adapter
  -> External Provider

Provider Webhooks
  -> Webhook Handler
  -> Status Update Events
  -> Notification DB

Monitoring
  -> Metrics, Logs, Tracing, Alerts
```

---

## 39. Minimalny diagram logiczny

```text
┌──────────────────────┐
│ Source Services      │
└──────────┬───────────┘
           │ POST /notifications
           ▼
┌──────────────────────┐
│ Notification API     │
│ - auth               │
│ - validation         │
│ - idempotency        │
└──────────┬───────────┘
           │ transaction
           ▼
┌──────────────────────┐
│ PostgreSQL           │
│ notification_request │
│ outbox_events        │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Outbox Publisher     │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Message Broker       │
└─────┬────────┬────────┘
      │        │
      ▼        ▼
┌─────────┐ ┌─────────┐
│ Email   │ │ Push    │
│ Worker  │ │ Worker  │
└────┬────┘ └────┬────┘
     │           │
     ▼           ▼
┌─────────┐ ┌─────────┐
│ SES /   │ │ FCM /   │
│ SendGrid│ │ APNs    │
└─────────┘ └─────────┘
```

---

## 40. Podsumowanie decyzji architektonicznych

| Obszar               | Decyzja                                 |
|----------------------|-----------------------------------------|
| Processing           | asynchroniczny                          |
| Queue                | Kafka/SQS/RabbitMQ                      |
| DB                   | PostgreSQL                              |
| Cache                | Redis                                   |
| Reliability          | outbox + retry + DLQ                    |
| Delivery semantics   | at-least-once                           |
| Duplicate prevention | idempotency key + dedupe                |
| Scaling              | osobne workery per kanał i priorytet    |
| Provider integration | adapter layer                           |
| Status tracking      | osobna tabela jobs + events             |
| Compliance           | preferences + unsubscribe + suppression |
| Observability        | metrics, logs, tracing, alerts          |

Najważniejsza uwaga: **to nie powinien być prosty wrapper na SendGrid/Twilio**. Dobry Notification System to pipeline z
kolejkami, idempotencją, preferencjami, retry, audytem i kontrolą kosztów. Bez tych elementów system może działać w
demo, ale będzie sprawiał problemy produkcyjnie.
