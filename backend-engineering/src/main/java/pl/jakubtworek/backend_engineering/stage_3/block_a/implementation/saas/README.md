# saas

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `temat-zaawansowany`
> - **Uczy:** saas.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „saas” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=DataGovernancePolicyTest,DataLifecycleCoordinatorTest,TenantCacheAndMetricsTest" test`
> - **Role klas:** brak klasy-kontrprzykładu; pozostałe typy są minimalnymi modelami pojęć opisanych niżej.
> - **Granica:** Model weryfikuje nazwany niezmiennik; nie implementuje produkcyjnego protokołu rozproszonego ani infrastruktury dostawcy.
<!-- material-card:end -->

## Wielodostępność i cykl życia danych



## Cel laboratorium

System SaaS nie staje się wielodostępny tylko dlatego, że każda tabela ma kolumnę
`tenant_id`. Granica tenanta musi być zachowana w uwierzytelnieniu, zapytaniach,
cache, kolejkach, limitach, telemetrii, zadaniach administracyjnych i procesach
usuwania danych. Ten pakiet pokazuje najważniejsze gwarancje bez udawania kompletnej
platformy zgodności regulacyjnej.

```text
zweryfikowana tożsamość
  → TenantRequestContext
  → tenant-scoped repository/cache/event
  → per-tenant quota
  → audit dostępu bez kopiowania PII

żądanie usunięcia
  → anonimizacja źródła prawdy
  → eviction cache
  → zdarzenie bez PII
  → tombstone chroniący restore backupu
  → audyt wykonania
```

## Mapa kodu

| Element | Odpowiedzialność | Gwarancja pokazywana przez test |
| --- | --- | --- |
| `TenantId`, `TenantRequestContext` | jawny kontekst dostępu pochodzący z uwierzytelnionej tożsamości | tenant nie jest dowolnym parametrem biznesowym |
| `TenantDataRepository` | klucz złożony z tenanta i identyfikatora obiektu | ten sam `subjectId` może bezpiecznie istnieć u wielu tenantów |
| `TenantDataService` | kontrola granicy przed odczytem | próba cross-tenant kończy się odmową i audytem |
| `TenantConcurrencyQuota` | osobny semaphore dla każdego tenanta | noisy tenant nie odbiera permitów pozostałym |
| `TenantCacheKey` | tenant jako obowiązkowy segment klucza | brak kolizji cache między tenantami |
| `TenantMetricPolicy` | plan i stabilny kubełek zamiast surowego `tenant_id` | liczba wartości labela jest ograniczona |
| `DataGovernancePolicy` | klasyfikacja, cel, retention i sposób usunięcia | polityka danych jest wykonywalnym kontraktem |
| `DataLifecycleCoordinator` | idempotentna propagacja usunięcia | retry nie duplikuje eventu, tombstone'a ani audytu |

## Izolacja tenantów

Najważniejsza reguła brzmi: `tenant_id` należy wyprowadzić ze zweryfikowanej
tożsamości, membershipu lub mapowania API key, a nie ufać dowolnemu nagłówkowi
przesłanemu przez klienta. `TenantRequestContext` reprezentuje wynik tej weryfikacji.
Każdy port operujący na danych przyjmuje `TenantId` jawnie — także joby, konsumenci
wiadomości i narzędzia administracyjne.

Popularne poziomy izolacji mają różny koszt:

| Model | Zaleta | Ryzyko / koszt |
| --- | --- | --- |
| wspólne tabele, kolumna `tenant_id` | niski koszt i proste migracje | każdy brak predykatu może ujawnić cudze dane |
| osobny schema | mocniejsza granica logiczna | migracje i pule połączeń rosną z liczbą tenantów |
| osobna baza / konto chmurowe | silny blast-radius i opcje residency | najwyższy koszt operacyjny i trudniejsze raportowanie |
| model hybrydowy | duzi lub regulowani klienci mogą być wydzieleni | routing i operacje muszą obsłużyć kilka klas storage |

W wariancie shared-schema samo `WHERE tenant_id = ?` nie wystarcza jako jedyna
ochrona. Typowa obrona warstwowa obejmuje:

- klucz główny lub unikalny zawierający `tenant_id`,
- indeksy zaczynające się od `tenant_id` dla tenant-scoped access patterns,
- obowiązkowy tenant w API repozytorium,
- PostgreSQL Row-Level Security ustawiane w każdej transakcji,
- testy cross-tenant oraz przegląd zapytań administracyjnych,
- tenant w envelope eventu i w kluczu partycjonowania.

RLS jest defence in depth, nie zastępuje autoryzacji aplikacyjnej. Trzeba również
uważać na właściciela tabeli, role z `BYPASSRLS`, pooling połączeń i wyczyszczenie
session context przed zwrotem połączenia do puli.

## Noisy neighbor i quota

Jeden rate limit na wejściu chroni liczbę requestów, ale nie musi chronić kosztu.
Tenant może wykonywać mniej requestów, które zajmują wszystkie połączenia DB albo
generują ogromne eksporty. Dojrzała polityka może osobno limitować:

- request rate i burst,
- liczbę operacji współbieżnych,
- koszt zapytań, eksportów i raportów,
- pojemność kolejki oraz liczbę zadań w toku,
- storage, transfer i koszt zewnętrznych API.

`TenantConcurrencyQuota` jest per-tenant bulkheadem fail-fast. Nie tworzy kolejnych
wątków i nie każe zdrowemu tenantowi czekać za przeciążonym. W produkcji limit
globalny dla klastra wymaga współdzielonego, atomowego stanu albo routingu ruchu;
lokalny semaphore ogranicza tylko jedną instancję. Trzeba też zaplanować globalny
limit systemu, aby tysiące poprawnych limitów tenantów łącznie nie przeciążyły bazy.

## Cache i telemetria

Klucz cache bez tenanta jest wyciekiem danych, nawet jeśli baza ma poprawny filtr.
`TenantCacheKey` wymusza schemat:

```text
subject:tenant:alpha-co:resource:customer-1
```

Tenant musi występować również w kluczach deduplikacji, idempotency, lockach i
materialized views. Eviction po zmianie danych musi usuwać dokładnie namespace danego
tenanta.

Metryki wymagają innego kompromisu. Surowy `tenant_id` jako label Prometheusa tworzy
nieograniczoną kardynalność i może ujawniać identyfikatory klientów. Laboratorium
emituje `tenant_plan`, ograniczony `tenant_bucket` i `outcome`. Dokładny tenant może
trafić do chronionego logu, trace'a lub audytu z kontrolą dostępu i retention. Dla
kilku najważniejszych tenantów można utrzymywać jawny allowlist, ale nie dynamiczny
label dla każdego nowego klienta.

## Klasyfikacja PII i retention

Przed zapisaniem danych trzeba znać nie tylko typ Java, lecz także semantykę:

| Pytanie | Przykład odpowiedzi |
| --- | --- |
| klasyfikacja | `PUBLIC`, `INTERNAL`, `PII`, `SENSITIVE_PII` |
| cel przetwarzania | realizacja umowy, support, bezpieczeństwo |
| retention | 30 dni, czas umowy + okres roszczeń |
| sposób zakończenia | delete, anonimizacja, uzasadnione zachowanie |
| właściciel polityki | zespół domenowy wraz z security/privacy/legal |

`DataGovernancePolicy` wymusza jawne podanie tych elementów. Sam enum nie rozstrzyga
zgodności z prawem. Konkretne okresy, podstawy i wyjątki zależą od jurysdykcji,
umowy, rodzaju danych oraz legal hold i muszą zostać zatwierdzone poza kodem.

Anonimizacja powinna być nieodwracalna. Pseudonimizacja (np. stabilny hash) nadal
może pozwalać na powiązanie rekordów i zwykle nie oznacza, że dane przestały być
osobowe. W przykładzie zachowany zostaje techniczny `subjectId`, więc system
produkcyjny musi osobno ocenić, czy nadal umożliwia identyfikację.

## Usunięcie jest przepływem rozproszonym

`DataLifecycleCoordinator` pokazuje pięć skutków jednego żądania:

1. źródło prawdy anonimizuje pola osobowe,
2. cache zostaje unieważniony tenant-aware kluczem,
3. event bez PII informuje projekcje i downstreamy,
4. rejestr backupów zapisuje tombstone obowiązujący do końca retention,
5. audyt zapisuje wykonawcę, cel operacji i referencję, ale nie kopię usuwanych danych.

Każdy skutek używa `deletionId` jako klucza idempotencji. W prawdziwym systemie nie
da się atomowo objąć jedną transakcją bazy, Redis, brokera i backupu. Stan workflow
powinien być trwały, publikacja powinna korzystać z outboxa, a retry musi wznawiać
brakujący krok. Test pojedynczej metody nie zastępuje testów awarii pomiędzy krokami.

Immutable backupów zwykle nie edytuje się w miejscu, bo złamałoby to ich integralność
i mogło usunąć materiał potrzebny do recovery. Zamiast tego:

- backup ma ograniczony i udokumentowany retention,
- dostęp do niego jest ściśle kontrolowany i audytowany,
- tombstone lub lista suppression jest stosowana po każdym restore,
- po odtworzeniu usunięte rekordy nie wracają do aktywnego systemu,
- przy odpowiednim modelu możliwe jest cryptographic erasure przez zniszczenie klucza.

Zdarzenie usunięcia również nie powinno zawierać danych, które właśnie usuwamy.
Konsumenci muszą potwierdzić wykonanie albo raportować zaległości, a katalog danych
powinien wskazywać wszystkie kopie, projekcje, eksporty i systemy analityczne.

## Audyt dostępu

Audyt różni się od zwykłego logu diagnostycznego. Powinien odpowiadać: kto, w jakim
tenancie, kiedy, do jakiego obiektu, w jakim celu i z jakim wynikiem uzyskał dostęp.
Jednocześnie nie powinien kopiować treści rekordu ani sekretów. Produkcyjny audit log
wymaga ochrony przed modyfikacją, ograniczonego dostępu, kontroli czasu, retention,
monitorowania luk i procedury przeglądu. Audytuj także odmowy, działania supportu,
eksporty masowe, impersonation i użycie trybu break-glass.

## Wykonywalne scenariusze

| Test | Co dowodzi |
| --- | --- |
| `TenantIsolationAndAuditTest` | identyczny business ID nie miesza tenantów, a próba cross-tenant jest odrzucona i audytowana |
| `TenantCapacityIsolationTest` | noisy tenant wyczerpuje tylko własny limit, a permit jest zwalniany dokładnie raz |
| `TenantCacheAndMetricsTest` | cache key zawiera tenant, a 1000 tenantów daje najwyżej 16 wartości labela |
| `DataGovernancePolicyTest` | klasyfikacja, purpose, retention i erasure są jawne |
| `DataLifecycleCoordinatorTest` | usunięcie dociera do wszystkich warstw i pozostaje idempotentne przy retry |

```shell
./mvnw -Dtest='*saas*' test
```

Na PowerShell:

```powershell
.\mvnw.cmd --% -Dtest=*saas* test
```

## Granice laboratorium

- Repozytorium i sinki są pamięciowe; nie dowodzą właściwości PostgreSQL, RLS,
  Redis, brokera ani obiektowego storage.
- Quota jest lokalna dla jednej JVM i ma ten sam limit dla każdego planu.
- Workflow nie ma trwałej maszyny stanów ani symulacji awarii po konkretnym kroku.
- Przykład nie implementuje residency, encryption per tenant, legal hold, eksportu
  danych, zgód ani pełnego katalogu data lineage.
- Materiał opisuje decyzje inżynierskie, nie jest poradą prawną ani certyfikatem
  zgodności z GDPR, CCPA, HIPAA czy innym reżimem.

Powiązane laboratoria: [kontrola przeciążenia](../overload/README.md),
[outbox i semantyka zdarzeń](../../../../stage_2/block_b/README.md),
[observability](../../../block_b/README.md) oraz
[disaster recovery](../../../block_c/README.md).
