# Domknięcie pięciu obszarów roadmapy — 25 października 2026

Rozwinięcie [wcześniejszych poprawek](remediation-2026-10-25.md).
Etapy pozostały bez zmian. Nowy zakres jest przypisany do istniejących bloków
i zastosowany w projektach odpowiednio do ich poziomu.

## Co działa

| Obszar | Implementacja i materiał | Dowód |
| --- | --- | --- |
| Tekst i Unicode | [laboratorium 1D](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/text_contracts/README.md), CustomerTextPolicy w [projekcie 1](../projects/project-1/docs/text-contracts.md) | NFC/NFKC, UTF-16/code points/UTF-8, strict decoder, locale; prawdziwy PostgreSQL normalize/collation C/keyset oraz zapis nazwy przez JDBC |
| Zewnętrzna tożsamość | [OIDC marketplace](../projects/project-2/docs/identity.md), [Resource Server i client credentials ticketingu](../projects/project-3/docs/identity.md) | prawdziwy Keycloak 26.6.2, discovery, rotacja RSA/JWKS, warm/cold cache przy awarii, required sub/exp/iss/aud; HTTP ownership i zabezpieczona saga przez gateway oraz bezpośrednie adresy |
| Lifecycle danych | [kontakt Notification](../projects/project-2/docs/contact-lifecycle.md), V8, cache LRU, source/projection/registry/outbox, gate w modelu SaaS | rollback zewnętrznej transakcji bez wycieku do cache, retry, błąd brokera, rzeczywista Kafka, pg_dump/restore starszej bazy, aktualny manifest suppression; osobny proces maintenance |
| Eksploatacja i SLO | [workload i recovery](../projects/project-3/docs/endurance-and-cost.md), CheckoutMetrics, BurnRatePolicy, PromQL/runbook i promtool fixtures | otwarty napływ, broker outage, zasoby, globalny niezmiennik inventory i zgodność PAID/payment/reservation/outbox/inbox; alert/no-alert/zero traffic |
| Ekonomia jednostkowa | UnitEconomics i CSV obserwacji, [wariant zasobów](../projects/project-3/docker-compose.cost-candidate.yml) | denominator unikalnych zakończonych operacji, UNKNOWN bez danych, odmowa mieszania walut i niespójnych liczników; wybór dopiero po filtrze SLO/p99 |

[Samodzielne zadania](engineering-practice/capstones.md) wymagają kontraktu,
kontrprzykładu, eksperymentu, RFC i review dla każdego z trzech projektów.
Nie wpisano fikcyjnego review przez człowieka ani doświadczeń zawodowych.

## Weryfikacja kodu i narzędzi

Komendy uruchamiano z katalogu danego modułu. Host używał JDK 23 z release 21,
obrazy aplikacji Java 21. Protoc, agenty JVM, Docker i HTTP były wykonywane
poza ograniczeniami sandboxa; końcowy skrypt HTTP w osobnym procesie pwsh.

| Katalog | Komenda | Wynik |
| --- | --- | --- |
| backend-engineering | `mvnw.cmd --batch-mode --no-transfer-progress verify` | sukces: 479 testów, 1 pominięty |
| backend-engineering | `mvnw.cmd --batch-mode --no-transfer-progress -Pinfrastructure-tests verify` | sukces: 20 testów prawdziwych silników |
| projects/project-1 | `mvnw.cmd --batch-mode --no-transfer-progress -Pinfrastructure-tests verify` i ukierunkowany `-Dit.test=PostgreSqlReservationLifecycleIT` | sukces: 108 szybkich testów (1 pominięty), kontrakty infrastruktury i dodatkowy zapis Unicode na PostgreSQL |
| projects/project-2 | `gradlew.bat test integrationTest check assemble` | sukces: unit, architektura, PostgreSQL/Kafka/Keycloak/HTTP i artefakty |
| projects/project-2 | `gradlew.bat contactMaintenance` z operation export/suppress na osobnych prywatnych bazach | sukces rzeczywistych procesów CLI; odtworzony source i projection bez PII |
| projects/project-3 | `mvnw.cmd verify` i osobno `-Pinfrastructure-tests verify`, końcowy profil common po zmianie fixture | sukces ośmiu modułów i końcowych kontraktów IdP |
| projects/project-3 | `promtool test rules checkout-slo.test.yml` z obrazu Prometheus v2.55.1 | sukces: cel, przekroczenie budżetu i zero ruchu |
| projects/project-3 | `pwsh -NoProfile -File scripts/identity-drill.ps1` | sukces: discovery, tożsamość, ownership, service-to-service PAID i retry |
| projects/project-3 | `java -cp common/target/classes pl.jakubtworek.backend.common.operations.UnitEconomics target/endurance/comparison.csv 0.999 3000` | oba pomiary spełniają filtr; wybrany endurance-e5f905961c87 przy zadanym modelu ceny |
| repozytorium | `scripts/check-documentation.ps1`, `backend-engineering/scripts/update-material-cards.ps1 -Check`, `git diff --check` | sukces: 322 dokumenty, 1854 lokalne odsyłacze, 11 profili, 114 kart materiałów; bez błędów whitespace |

Projekt 2 ma końcowo 100 testów jednostkowych/architektury i 24 integracyjne,
bez błędów ani pominięć. Sprawdzono również parser obu nowych skryptów PowerShell
oraz konfiguracje Compose z każdym z dwóch overlayów.

Pełny build wykrył też błąd fixture: jawna lista client scopes w Keycloak
usunęła domyślny mapper sub. Dodano scope basic z właściwym mapperem oraz
walidację niepustego sub po stronie Resource Server. Brak principal jest odmową,
a nie NullPointerException. Błąd tworzenia dat w negatywnym teście HMAC poprawiono
w fixture; końcowa suite sprawdza przez HTTP brak sub oraz exp.

## Obserwacje eksploatacyjne

Baseline: run endurance-fb3680586d3a, 120 s stałego ruchu + 15 s awarii brokera
oraz 120 s recovery, 2 logiczne checkouty/s. Generator zakończył 510 operacji,
510 było good w limicie 3 s, bez dropped iterations. p99 podróży: 353.46 ms.
Końcowy walidator wymagał i znalazł 510 PAID, 510 płatności, 510 potwierdzonych
rezerwacji i 510 markerów inboxa. Zachowana została suma inventory.

Zapisano surowe summary, stdout/stderr oraz próbki CPU/memory/network/block I/O
w ignorowanym target/endurance. Obserwowane okno kosztowe objęło 354 s łącznie
ze startem generatora i walidacją/drain. Przy jawnym przykładowym 1 PLN/h:
0.098333... PLN przypisanego kosztu, 0.00019281 PLN na ukończone zamówienie.
To demonstracja estymacji, nie cena chmury ani wynik rachunku.

Próba wariantu ujawniła lukę harnessu: kontenery running bez Docker HEALTHCHECK
nie były jeszcze gotowe na HTTP. Generator wymaga teraz readiness wszystkich
sześciu usług i sprawdza, czy nie trafił na secure API z losowym userId.
Nie obniżono progów ani nie pominięto błędnych żądań. Nieudany run zachowuje
diagnostykę; spójne pomiary kosztu mogą być zapisane również po błędzie progów.

Wariant kontrolowany: Order Service 0.75 CPU, 1 GiB, ten sam rate i długości faz
co baseline. Jawna ilustracyjna alokacja ceny: 0.90 kosztu wspólnego + 0.075
Order Service = 0.975 PLN/h. Końcowy run endurance-e5f905961c87 także przeszedł
wszystkie niezmienniki: 510 good, 510 unikalnych PAID, 510 płatności,
510 potwierdzonych rezerwacji i 510 markerów inboxa, bez dropped iterations.

| Obserwacja | Baseline | Order Service 0.75 CPU / 1 GiB |
| --- | --- | --- |
| Logiczne operacje / good / zakończone PAID | 510 / 510 / 510 | 510 / 510 / 510 |
| p99 podróży | 353.46 ms | 331.91 ms |
| Pełne okno alokacji kosztu | 354 s | 354 s |
| Założona cena środowiska | 1.000 PLN/h | 0.975 PLN/h |
| Przypisany koszt | 0.098333... PLN | 0.095875 PLN |
| Koszt unikalnego PAID | 0.00019281 PLN | 0.00018799 PLN |

Oba pomiary spełniają filtr empiryczny good/eligible >= 0.999 oraz p99 <= 3000 ms.
Przy tym modelu ceny wariant ma o 2.5% niższy koszt jednostkowy. Jedna para krótkich
przebiegów nie dowodzi większej wydajności wariantu, stabilnego 30-dniowego SLO
ani rzeczywistych oszczędności na rachunku. Wynik uzasadnia dalszy pomiar capacity,
a nie automatyczne obniżenie zasobów środowiska produkcyjnego.

## Granice

- Kontakt Notification jest określonym zakresem usunięcia. Nie deklarujemy
  usunięcia wszystkich referencji finansowych, identyfikatorów i audytu organizacji.
- Manifest registry musi być aktualny i chroniony niezależnie od body/backupu.
  Hash nie uwierzytelnia równocześnie podmienionych obu plików. Brama maintenance
  wymaga zamkniętego ruchu i workerów; flaga nie zastępuje izolacji infrastruktury.
- Keycloak start-dev, HTTP, password grant fixture i jawne credentiale są lokalnym
  testem. Produkcja wymaga TLS, Authorization Code/PKCE tam, gdzie dotyczy, osobnych
  tożsamości usług oraz minimalnych scopes/audience. Cache JWT/JWKS nie daje
  natychmiastowego odwołania tokenu. Nie wdrażano produkcyjnego IdP ani cloud HA.
- Kilkuminutowy workload nie dowodzi wielogodzinnej stabilności ani 30-dniowego
  SLO. Domyślny scenariusz ma około 30 min i wymaga odpowiedniego inventory.
- Testy promtool badają reguły i dane syntetyczne. Nie wysyłają alertu do on-call.
- CI zawiera kontrakty i manualny job z dwoma wariantami workloadu/tożsamością.
  Raport dokumentuje weryfikację lokalną; nie potwierdza wyniku zdalnego CI.

Raport powstał przed publikacją commitów. Podczas opisanej weryfikacji wcześniejsze
zmiany użytkownika i pliki IDE zachowano. Prywatne kontenery i dane środowiska
roadmap-completion-20261025 usunięto po zakończeniu prób. Surowe wyniki pozostały
w ignorowanych katalogach target/build; nie dodano ich do plików wersjonowanych.
