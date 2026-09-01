# progressive delivery

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `praktyka-produkcyjna`
> - **Uczy:** progressive delivery.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „progressive delivery” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=CanaryAndRollbackTest,FeatureFlagTest,GameDayAndIncidentTest" test`
> - **Role klas:** `ProgressiveDeliveryController` = `production-boundary`.
> - **Granica:** Laboratorium pokazuje kontrakt i failure modes; nie zastępuje pełnego testu end-to-end ani operacyjnej konfiguracji środowiska.
<!-- material-card:end -->

## Progressive delivery i operacje podczas incydentu



To laboratorium zaczyna się tam, gdzie kończy się poprawny `Deployment`. Sam fakt,
że nowe Pody są ready, nie dowodzi poprawności biznesowej ani braku regresji latency.
Promocja wersji powinna być osobną, obserwowalną decyzją:

```text
immutable candidate
  → mały canary traffic
  → porównywalne okno baseline/canary
  → HOLD | PROMOTE | ROLLBACK
  → timeline, weryfikacja recovery i dowody
```

## Mapa kodu

| Element | Odpowiedzialność | Zachowanie fail-closed |
| --- | --- | --- |
| `CanaryAnalyzer` | porównuje error rate i p99 z baseline | mała próbka zatrzymuje promocję, regresja wymusza rollback |
| `ProgressiveDeliveryController` | zarządza ruchem stable/candidate | rollback ustawia candidate traffic na 0% |
| `FeatureFlag` | stabilny percentage rollout | kill switch zawsze wygrywa z targetingiem |
| `ShadowTrafficRouter` | kopiuje oczyszczony request do kandydata | błąd shadow nie zmienia odpowiedzi użytkownika |
| `SchemaCompatibilityValidator` | sprawdza stare i nowe repliki wobec live schema | blokuje contract i rollback niezgodny ze schematem |
| `FaultInjector` / `GameDayPlan` | kontrolowana awaria i jej safety contract | limit blast radius, czasu, approval oraz abort/recovery |
| `IncidentTimeline` | chronologiczne fakty i dowody | odrzuca zdarzenia dopisane w złej kolejności |
| `IncidentRunbook` | wykonywalna sekwencja mitigacji | brak lub zła kolejność kroku przerywa scenariusz |
| `PostmortemValidator` | kompletność analizy i działań | wymaga impactu, timeline’u, ownera i terminu |

## Canary analysis

Baseline i canary muszą pochodzić z porównywalnego czasu, regionu, endpointów i klas
ruchu. W przeciwnym razie analiza porównuje zmianę wersji ze zmianą workloadu.
`CanaryAnalyzer` używa czterech barier:

1. minimalna liczba requestów w obu oknach — za mała próbka daje `HOLD`;
2. absolutny maksymalny error rate;
3. dopuszczalna regresja error rate względem stable;
4. dopuszczalny stosunek p99 canary do p99 stable.

Średnia latency nie wykrywa problemów ogona. Sam p99 także nie wystarcza: realna
polityka powinna uwzględniać SLO, saturation, krytyczne KPI biznesowe, brak danych i
kilka kolejnych okien. Brak telemetry powinien zatrzymać promocję, a nie oznaczać
zdrowego wdrożenia.

`ProgressiveDeliveryController` automatycznie przywraca 100% ruchu stable po decyzji
`ROLLBACK`. Nie usuwa jednak kandydata ani nie cofa danych, dzięki czemu można zebrać
dowody. Rollback powinien być idempotentny, audytowany i ograniczony czasowo; operator
musi mieć możliwość ręcznego zatrzymania automatyzacji.

## Feature flag i kill switch

Feature flag oddziela deployment kodu od ekspozycji zachowania. `FeatureFlag` używa
stabilnego hasha `flag + subject`, dzięki czemu ten sam tenant nie skacze losowo między
wariantami. Najważniejsza reguła brzmi:

```text
kill switch > global enabled > targeting/percentage
```

Kill switch powinien mieć bezpieczną wartość domyślną, lokalny cache z określonym TTL,
audyt zmian, RBAC i test działania podczas niedostępności systemu flag. Flaga nie jest
mechanizmem autoryzacji. Po zakończeniu rolloutu należy usunąć flagę i martwą gałąź
kodu; inaczej liczba możliwych stanów rośnie wykładniczo.

## Shadow traffic bez efektów biznesowych

Shadow służy do porównania zachowania kandydata pod realnym kształtem ruchu, ale jego
wynik nie wraca do użytkownika. `ShadowTrafficRouter`:

- najpierw wykonuje primary i zawsze zwraca jego odpowiedź;
- planuje shadow na osobnym executorze, więc kandydat nie wydłuża primary latency;
- przekazuje shadow osobny typ bez nagłówka Authorization;
- nie udostępnia handlerowi portu zapisu ani capability wykonania efektu;
- raportuje błąd kandydata osobnym obserwatorem.

W produkcji trzeba dodatkowo usunąć PII, ograniczyć sampling i koszt, nadać shadow
osobną pulę/bulkhead oraz zablokować publikację eventów, maile, płatności i inne
integracje. Nagłówek `X-Shadow=true` nie jest zabezpieczeniem, jeżeli kod nadal ma
credentials do produkcyjnych systemów. Najbezpieczniejszy jest read-only sandbox albo
adaptery, które technicznie nie posiadają capability zapisu.

## Schemat podczas mieszanego rolloutu

Podczas canary jednocześnie działają co najmniej dwie wersje aplikacji. Live schema
musi należeć do wspólnego zakresu kompatybilności obu wersji:

```text
v1 obsługuje schema 1..2
v2 obsługuje schema 2..3

rollout na schema 2       → bezpieczny
contract do schema 3      → dopiero po usunięciu v1
rollback v2 → v1 na 3     → niedozwolony
```

`SchemaCompatibilityValidator` wykonuje dokładnie tę kontrolę. Numer wersji jest
uproszczeniem. W realnym systemie trzeba przetestować kolumny, constrainty, format
eventów, dual read/write, backfill i zachowanie ORM. Bezpieczna sekwencja to osobne
wdrożenia: expand → kompatybilny kod → backfill/verify → contract. Destrukcyjna down
migration nie jest domyślnym rollbackiem aplikacji.

Laboratorium migracji znajduje się w
[Stage 1D](../../../stage_1/block_d/sql/migration/README.md), a rollback w DR w
[Stage 3C](../../../stage_3/block_c/README.md).

## Fault injection i game day

`FaultInjector` psuje dokładnie zaplanowaną liczbę wywołań, dzięki czemu test jest
deterministyczny. `GameDayPlan` wymaga przed eksperymentem:

- falsyfikowalnej hipotezy;
- dokładnego celu i małego blast radius;
- maksymalnego czasu;
- mierzalnego abort condition;
- recovery action oraz jawnego approval.

Game day nie powinien zaczynać się od „wyłączmy coś i zobaczmy”. Najpierw potwierdza
się observability, on-call, backup plan, brak nakładającej się zmiany i prawo do
natychmiastowego przerwania. Fault injection w produkcji bez tych granic jest
niekontrolowanym incydentem, nie eksperymentem.

## Operacje podczas incydentu

Priorytetem jest ograniczenie wpływu, nie znalezienie root cause w pierwszych minutach.
Minimalny przepływ scenariusza jest jawny:

```text
declare incident
  → stop rollout
  → activate kill switch
  → capture evidence
  → verify recovery against baseline/SLO
```

`IncidentRunbook` wykonuje tę kolejność i odrzuca runbook, który weryfikuje recovery
przed mitigacją albo nie zbiera dowodów. Prawdziwy runbook powinien zawierać komendy,
dashboardy, ownership, warunki eskalacji, expected output i bezpieczną alternatywę —
nie tylko „sprawdź logi”. Test scenariuszowy chroni runbook przed dezaktualizacją po
zmianie nazw flag, metryk, deploymentów i procedur.

`IncidentTimeline` zapisuje fakty z timestampami, źródłem dowodu i zastosowaną zmianą.
Należy oddzielać obserwację („p99 wzrosło o 40%”) od hipotezy („prawdopodobnie pula”).
Chronologia pozwala policzyć time to detect, mitigate i recover oraz ustalić, które
działanie rzeczywiście poprzedziło poprawę.

## Postmortem

Postmortem nie jest winą jednej osoby ani streszczeniem czatu. `PostmortemValidator`
wymaga:

- mierzalnego impactu i czasu trwania;
- technicznej przyczyny oraz contributing factors;
- timeline’u obejmującego detection, mitigation i recovery;
- działań z ownerem i przyszłym terminem.

Dobre action items zmieniają system: automatyzację, limity, testy, dashboard lub
runbook. „Uważać następnym razem” nie ma ownera, dowodu ukończenia ani trwałego wpływu.

## Test scenariuszowy

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=CanaryAndRollbackTest,ShadowTrafficTest,FeatureFlagTest,SchemaCompatibilityTest,GameDayAndIncidentTest" test
```

Przekrojowy test wykonuje runbook, buduje timeline, liczy 150 sekund do recovery i
waliduje postmortem. Osobne testy udowadniają automatyczny rollback, kill switch,
read-only shadow, blokadę contract migration oraz odrzucenie niezatwierdzonego game day.

## Granice laboratorium

- Model nie steruje prawdziwym Kubernetesem ani service meshem.
- Error rate i p99 są wejściem z telemetry, nie są tu obliczane z Prometheusa.
- Typ read-only ogranicza capability w przykładzie, ale nie zastępuje IAM i izolacji sieci.
- Test runbooka nie zastępuje ćwiczenia z ludźmi, presją czasu i prawdziwymi narzędziami.
- Pełny pipeline telemetry znajduje się w [Stage 3B](../../../stage_3/block_b/README.md),
  a bezpieczny supply chain w [Stage 2D](../../block_d/README.md).
