# Materiały do nauki wzorców

[README](README.md) · [Indeks](INDEKS.md) · [Laboratoria](LABORATORIA.md)

Najpierw przeczytaj kartę, prześledź kod i scenariusz poprawności w Markdown. Następnie wybierz jedną lekturę
przewodnią, a dokumentację wykorzystaj do sprawdzenia konkretnego kontraktu. Książki mogą wymagać zakupu lub
dostępu bibliotecznego; bezpłatna ścieżka jest wskazana osobno. Większość źródeł jest po angielsku. Materiały
autorów i katalogi wzorców są źródłami nazw i pogłębienia; przykłady w tym module są autorskimi scenariuszami
edukacyjnymi.

Linki przewodnie sprawdzono 25.10.2026. Oznaczenie wydania dokumentacji jest ważniejsze niż data odczytu:
przykłady opisują Java 21, a starsze książki mogą używać C++, starszej Javy, innej infrastruktury lub
nazewnictwa. Automatyczny checker sprawdza lokalne odsyłacze, nie dostępność internetu.

## gof

**Zakres:** [kreacyjne](catalog/01-creational/README.md), [strukturalne](catalog/02-structural/README.md),
[behawioralne](catalog/03-behavioral/README.md).

**Lektura przewodnia:** Erich Gamma, Richard Helm, Ralph Johnson i John Vlissides,
[*Design Patterns: Elements of Reusable Object-Oriented Software*](https://www.informit.com/store/design-patterns-elements-of-reusable-object-oriented-9780201633610).
Książka porządkuje klasyczne 23 GoF; kod historycznych języków przenoś na intencję i kontrakt, zamiast
kopiować hierarchie mechanicznie do Javy.

**Bezpłatna ścieżka:** karty GoF, [kod i scenariusze](LABORATORIA.md#1-gof-zobacz-co-faktycznie-się-zmienia)
oraz wersjonowane [API Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/index.html). W API szukaj
przykładów intencji, np. Iterator, dekorowanie strumieni i komparatory. Sama obecność metody o nazwie factory
nie dowodzi konkretnego wzorca GoF.

**Sprawdź:** zmień jedną oś projektu, np. kanał Bridge albo operację Visitora, i wskaż dokładnie, które klasy
musiały się zmienić oraz dlaczego.

## java

**Zakres:** [idiomy](catalog/04-java/README.md) i [współbieżność](catalog/17-concurrency/README.md).

**Lektura przewodnia:** Brian Goetz i współautorzy, [*Java Concurrency in Practice*](https://jcip.net/) —
bezpieczeństwo publikacji, stan współdzielony, anulowanie i organizacja pracy. Książka poprzedza virtual
threads; nie traktuj doboru historycznych pul jako instrukcji dla każdego workloadu.

**Bezpłatnie:**
[java.util.concurrent, Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/package-summary.html),
[AutoCloseable](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/AutoCloseable.html) i
[JEP 453](https://openjdk.org/jeps/453). JEP opisuje **preview w JDK 21**; StructuredTaskScope pozostaje
materiałem opisowym, bez buildu aplikacji. Status w późniejszych JDK trzeba sprawdzić osobno. Dla backpressure
czytaj [Reactive Streams](https://www.reactive-streams.org/).

**Sprawdź:** wyjaśnij, dlaczego finalna referencja do listy nie daje głębokiej niezmienności i dlaczego
anulowanie Future nie cofa zakończonej transakcji.

## ddd

**Zakres:** [model domenowy i relacje kontekstów](catalog/05-domain/README.md).

**Lektura przewodnia:** Eric Evans, *Domain-Driven Design: Tackling Complexity in the Heart of Software*;
materiały i odsyłacze autora są na [Domain Language — DDD Resources](https://www.domainlanguage.com/ddd/).

**Bezpłatnie:** dostępny tam *DDD Reference* do powtórek definicji i relacji między wzorcami. To skrót pojęć,
więc przy pierwszej nauce łącz go ze scenariuszami i niezmiennikami z kart, a nie samym zapamiętywaniem
słownika.

**Sprawdź:** określ granicę agregatu zamówienia i wyjaśnij, dlaczego zapas wszystkich produktów nie powinien
stać się częścią tego samego agregatu.

## architecture

**Zakres:** [architektura aplikacji i usług](catalog/06-architecture/README.md).

**Źródła przewodnie, bezpłatne:** Alistair Cockburn,
[oryginalny artykuł Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture) oraz James
Lewis i Martin Fowler, [Microservices](https://martinfowler.com/articles/microservices.html). Porównuj
kierunek zależności, właściciela danych i jednostkę wdrożenia; podobny diagram pudełek może ukrywać zupełnie
inne sprzężenia.

**Pogłębienie:**
[katalog wzorców mikroserwisowych Chrisa Richardsona](https://microservices.io/patterns/index.html) i
[Cloud Design Patterns](https://learn.microsoft.com/en-us/azure/architecture/patterns/). Przykłady platformowe
czytaj jako warianty realizacji, nie wymagania użycia Azure.

**Sprawdź:** prześledź przypadek użycia przez kontrolowany port bez sieci i bazy; opisz, co dodatkowo
kosztowałoby wyniesienie tego samego modułu do osobnej usługi.

## poeaa

**Zakres:** [persystencja](catalog/07-persistence/README.md),
[mapowanie obiektowo-relacyjne i sesje](catalog/15-mapping/README.md).

**Lektura przewodnia:** Martin Fowler, *Patterns of Enterprise Application Architecture*. Bezpłatny
[katalog autora](https://martinfowler.com/eaaCatalog/) jest punktem startowym i indeksem pogłębienia
książkowego.

**Praktyka:** zestaw Unit of Work i Identity Map z zakresem persistence context, ale nie zakładaj, że każdy
ORM realizuje wszystkie szczegóły identycznie. Do testów SQL użyj silnika i wersji danego projektu roadmapy;
ten moduł pokazuje lokalne modele, nie deklaruje zgodności ze wszystkimi bazami.

**Sprawdź:** porównaj Active Record i Data Mapper oraz dwa snapshoty z tą samą wersją. Pokaż, że flush może
być wykonany przed późniejszym rollback.

## eip

**Zakres:** [messaging](catalog/08-messaging/README.md) i
[style integracji oraz zarządzanie kanałami](catalog/18-integration-operations/README.md).

**Lektura przewodnia:** Gregor Hohpe i Bobby Woolf, *Enterprise Integration Patterns*. Bezpłatny
[katalog autorów](https://www.enterpriseintegrationpatterns.com/patterns/messaging/) porządkuje język kanałów,
komunikatów, transformacji i endpointów. Lokalne karty grupują powiązane warianty; nie są kopią spisu książki
ani implementacją konkretnego brokera.

**Praktyka:** prześledź wiadomość od producenta do commitu i ack odbiorcy. Narysuj osobno transport, granice
trwałości oraz decyzję biznesową.

**Sprawdź:** połącz Splitter, Content-based Router i Aggregator dla zamówienia z trzech magazynów. Zdefiniuj
timeout, duplikat i brak jednej odpowiedzi.

## microservices

**Zakres:** [spójność i procesy rozproszone](catalog/09-consistency/README.md).

**Bezpłatna ścieżka:** [Microservices Patterns](https://microservices.io/patterns/index.html) — szukaj
Transactional Outbox, Saga, CQRS i Event Sourcing; dodatkowo własne
[przebiegi awarii](LABORATORIA.md#3-outbox-i-inbox-dwukrotna-dostawa-jeden-lokalny-skutek).

**Pogłębienie:** *Designing Data-Intensive Applications*, [strona książki](https://dataintensive.net/) —
replikacja, transakcje, przetwarzanie danych i ograniczenia gwarancji. Dobierz rozdział do konkretnej decyzji;
nie jest konieczna lektura całości przed pierwszym modelem Outbox.

**Sprawdź:** przerwij publikację po dostarczeniu eventu i przed oznaczeniem wysłania. Wyjaśnij, dlaczego to
wymaga deduplikacji zamiast deklaracji exactly-once.

## resilience

**Zakres:** [odporność i przeciążenie](catalog/10-resilience/README.md).

**Bezpłatnie:** [Cloud Design Patterns](https://learn.microsoft.com/en-us/azure/architecture/patterns/) oraz
[Google SRE — rozdziały o overload i cascading failures](https://sre.google/sre-book/table-of-contents/).
Porównaj limit tempa, limit równoległości, odrzucenie pracy i ograniczenie retry: każdy odpowiada na inną
część problemu.

**Sprawdź:** policz liczbę prób w trzech warstwach po 3 próby każda. Potem zaproponuj jeden deadline, retry
budget i klasyfikację awarii.

## scaling

**Zakres:** [skalowanie i przetwarzanie danych](catalog/11-scaling/README.md).

**Lektura przewodnia:** [*Designing Data-Intensive Applications*](https://dataintensive.net/). Skup się na
partycjonowaniu, replikacji i konsekwencjach ich łączenia.

**Bezpłatnie:** [Google SRE](https://sre.google/sre-book/table-of-contents/) — load balancing, data processing
pipelines i data integrity; uzupełnij [studiami System Design w roadmapie](../system-design/README.md).

**Sprawdź:** wybierz shard key dla jednego dużego tenanta i wielu małych. Narysuj przeniesienie danych bez
utraty odczytów i z kontrolą właściciela zapisu.

## operations

**Zakres:** [wdrożenia, migracje i recovery](catalog/12-delivery/README.md).

**Bezpłatna lektura przewodnia:** [Google SRE](https://sre.google/sre-book/table-of-contents/) — release
engineering, monitoring, launch i testowanie niezawodności.
[Cloud Design Patterns](https://learn.microsoft.com/en-us/azure/architecture/patterns/) pomagają porównać
warianty health, izolacji i bezpiecznego dostarczania.

**Sprawdź:** zaplanuj canary zmiany zapisującej nowy format danych. Rozdziel wyłączenie funkcji, rollback kodu
i naprawę danych już zapisanych przez canary.

## security

**Zakres:** [bezpieczeństwo](catalog/13-security/README.md).

**Bezpłatna lektura przewodnia:** [OWASP Cheat Sheet Series](https://cheatsheetseries.owasp.org/index.html) —
szukaj Authorization, Multi-Tenant Security, SSRF Prevention, Secrets Management i Cryptographic Storage.
Czytaj wzorzec razem z granicą zaufania i scenariuszem nadużycia, a nie jako samą listę komponentów
bezpieczeństwa.

**Sprawdź:** poprawnie uwierzytelniony użytkownik zna ID cudzego zamówienia. Wskaż wszystkie miejsca
egzekwowania tenantId i pokaż oczekiwane odrzucenie.

## api

**Zakres:** [kontrakty API](catalog/16-api/README.md).

**Źródła normatywne, bezpłatne:** [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html)
oraz [RFC 9457 — Problem Details](https://www.rfc-editor.org/info/rfc9457). Rozdziel safe, idempotent,
conditional request i kompatybilność payloadu; status 202 nie jest potwierdzeniem zakończenia zadania.

**Sprawdź:** zaprojektuj dwa równoczesne PUT z tym samym If-Match i wyjaśnij, dlaczego jeden powinien zostać
odrzucony po zmianie wersji.

## testing

**Zakres:** [testowanie wzorców i systemów](catalog/14-testing/README.md).

**Bezpłatnie:** Martin Fowler, [Test Double](https://martinfowler.com/bliki/TestDouble.html), z odsyłaczami do
katalogu *xUnit Test Patterns* Gerarda Meszarosa;
[katalog mikroserwisowy](https://microservices.io/patterns/index.html) dla kontraktów konsumenta oraz
[Google SRE](https://sre.google/sre-book/table-of-contents/) dla eksperymentów recovery.

**Sprawdź:** nazwij niezmiennik i historię awarii przed napisaniem testu. Wskaż, czego test in-memory nie
dowodzi o transakcji, sieci ani trwałości.
