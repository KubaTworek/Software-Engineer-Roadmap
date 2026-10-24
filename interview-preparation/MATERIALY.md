# Książki i źródła do przygotowania do rozmów

[Rozdziały i sposób nauki](README.md) · [Indeks pytań](INDEKS.md)

Bibliografia uzupełnia 323 odpowiedzi, nie dodaje nowych kanonicznych pytań.
Najpierw odpowiedz z pamięci, następnie przeczytaj wskazany fragment źródła
i popraw odpowiedź o mechanizm, kontrprzykład oraz ograniczenie. Na końcu sprawdź
ją na przykładzie lub w powiązanym laboratorium. Zestaw nie jest listą zakupów
ani lektur do przeczytania w całości przed rozmową. Większość materiałów jest po angielsku.

## Książki przewodnie

Pozycje komercyjne są dostępne przez zakup, bibliotekę lub subskrypcję.
Bezpłatną ścieżkę zapewniają źródła pod każdym rozdziałem; pełny tekst I9 jest otwarty.

| ID | Książka | Do czego wracać |
| --- | --- | --- |
| I1 | Joshua Bloch, [Effective Java, wyd. 3](https://www.informit.com/store/effective-java-9780134685991) | Kontrakty obiektów, kolekcje, generyki, niezmienność, wyjątki i API. Obejmuje Javę do 9; nowsze funkcje weryfikuj w JDK/JLS. |
| I2 | Brian Goetz i współautorzy, [Java Concurrency in Practice](https://jcip.net/) | Publikacja, thread safety, złożone operacje, anulowanie i executory. Mechanizmy pozostają przydatne, ale książka nie opisuje virtual threads. |
| I3 | Scott Oaks, [Java Performance, wyd. 2](https://www.oreilly.com/library/view/java-performance-2nd/9781492056102/) | Metodyka pomiaru, JIT, GC, profilowanie i diagnostyka. Książka koncentruje się na Javie 8/11; flagi i kolektory porównuj z dokumentacją używanego runtime. |
| I4 | Markus Winand, [SQL Performance Explained](https://sql-performance-explained.com/) | Indeksy, predykaty, joiny, sortowanie i paginacja. Dołącz plan z konkretnego silnika; reguła z książki nie przewiduje automatycznie wyboru planera. |
| I5 | Martin Kleppmann, Chris Riccomini, [Designing Data-Intensive Applications, wyd. 2](https://www.oreilly.com/library/view/designing-data-intensive-applications/9781098119058/) | Transakcje, replikacja, partycjonowanie, strumienie i dane pochodne. Przy pierwszym wydaniu wybieraj temat, a nie numer rozdziału. |
| I6 | Martin Fowler, [Refactoring, wyd. 2](https://martinfowler.com/books/refactoring.html) | Małe zmiany zachowujące zachowanie, testy i uzasadnienie refaktoryzacji. Przykłady są w JavaScript; mechanikę trzeba świadomie przełożyć na Javę. |
| I7 | Chip Huyen, [AI Engineering](https://huyenchip.com/books/) | Ewaluacja, retrieval, latency, koszt i budowanie aplikacji z modeli bazowych. Kontrakt API, retencję i możliwości konkretnego modelu sprawdzaj oddzielnie. |
| I8 | Robert Sedgewick, Kevin Wayne, [Algorithms, wyd. 4](https://www.informit.com/store/algorithms-9780132762588) | Struktury danych, wyszukiwanie i grafy w Javie. Uzasadniaj rozwiązanie, zamiast zapamiętywać listing. |
| I9 | Google, [The Site Reliability Workbook](https://sre.google/workbook/table-of-contents/), bezpłatny pełny tekst | SLO, incydenty, alerty i praca operacyjna. Własne doświadczenie opisuj na podstawie rzeczywistych zdarzeń, nie historii z książki. |

Dalsze książki i mapy zastosowań: [12 studiów System Design](../system-design/MATERIALY.md)
oraz [LLD, struktury danych i pamięć](../low-level-design/MATERIALY.md).

## 01. Podstawy Javy

[Pytania](01-java.md). **Książka:** I1, kontrakty obiektów, typy i wyjątki.
**Bezpłatnie:** [Dev.java](https://dev.java/learn/), język i API, oraz
[JLS 21](https://docs.oracle.com/javase/specs/jls/se21/html/index.html), reguły języka.

**Sprawdź:** dlaczego record z polem List nie jest automatycznie głęboko
niemutowalny? Rozróżnij gwarancję języka, biblioteki i konkretną implementację JVM.

## 02. Kolekcje i Stream API

[Pytania](02-kolekcje-streamy.md). **Książki:** I1 i I8, równość, porządek i koszt operacji.
**Bezpłatnie:** [kolekcje JDK 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/package-summary.html)
oraz [Stream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html).

**Sprawdź:** co psuje mutacja klucza mapy i kiedy Comparator równy zero scala
elementy? Odróżnij źródło, operacje pośrednie i terminalne oraz kontrakt równoległego wykonania.

## 03. Współbieżność i model pamięci

[Pytania](03-wspolbieznosc.md). **Książka:** I2, thread safety i publikacja.
**Bezpłatnie:** [JLS 21, rozdział 17](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html)
oraz [java.util.concurrent](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/package-summary.html).

**Sprawdź:** wskaż konkretną krawędź happens-before. Pokaż, dlaczego volatile
nie chroni całego check-then-act i dlaczego kilka poprawnych przebiegów stress testu nie dowodzi braku wyścigu.

## 04. Async i virtual threads

[Pytania](04-async-virtual-threads.md). **Książka:** I2, executory i anulowanie,
uzupełniona o [Virtual Threads w JDK 21](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
oraz [CompletableFuture](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html).

**Sprawdź:** co nadal ogranicza przepustowość przy wielu virtual threads?
Oddziel timeout wyniku od zatrzymania pracy. Zachowania pinningu nie uogólniaj
między wersjami JDK; materiał o JDK 21 opisuje właśnie tę wersję.

## 05. JVM i diagnostyka

[Pytania](05-jvm-diagnostyka.md). **Książka:** I3, pomiar i analiza zamiast strojenia z pamięci.
**Bezpłatnie:** [JDK 21 Troubleshooting Guide](https://docs.oracle.com/en/java/javase/21/troubleshoot/)
oraz [JMH — projekt i przykłady](https://github.com/openjdk/jmh).

**Sprawdź:** jakie dane odróżnią wyciek heapu, pamięć natywną i nadmierną alokację?
Wskaż, czego nie rozstrzyga pojedynczy histogram lub thread dump z materiału diagnostycznego.

## 06. Spring i Spring Boot

[Pytania](06-spring-boot.md). **Pogłębienie książkowe:** I1/I6, kontrakty i testowalne granice.
**Źródło mechanizmu:** [Spring Framework 6.2 — proxy](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
oraz [Boot — tworzenie autokonfiguracji](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html).

**Sprawdź:** prześledź utworzenie beana i wywołanie przez proxy. Dlaczego
self-invocation zmienia wynik? Dokumentację Boota dobierz do badanego wydania.

## 07. Transakcje i JPA

[Pytania](07-transakcje-jpa.md). **Książki:** I5, transakcje; I4, koszt zapytań.
**Bezpłatnie:** [Spring 6.2 — propagacja](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/tx-propagation.html)
oraz [Hibernate ORM 6.6 User Guide](https://docs.hibernate.org/orm/6.6/userguide/html_single/).

**Sprawdź:** narysuj fizyczne transakcje, połączenia i persistence context
dla REQUIRED/REQUIRES_NEW. Oddziel flush od COMMIT i regułę frameworka od zachowania silnika.

## 08. SQL i bazy danych

[Pytania](08-bazy-danych.md). **Książka:** I4; I5 dla replikacji i skalowania.
**Bezpłatnie:** [Use The Index, Luke!](https://use-the-index-luke.com/), materiały autora I4,
oraz [PostgreSQL 16 — izolacja](https://www.postgresql.org/docs/16/transaction-iso.html).

**Sprawdź:** wyjaśnij różnicę estimated/actual rows i przyczynę dużej liczby
odczytanych wierszy. Zbuduj historię dwóch transakcji, zamiast podawać samą nazwę poziomu izolacji.

## 09. API i bezpieczeństwo

[Pytania](09-api-security.md). **Pogłębienie:** I1, kontrakty API; bezpieczeństwo protokołu ze źródeł.
**Bezpłatnie:** [RFC 9110 — HTTP](https://datatracker.ietf.org/doc/html/rfc9110),
[RFC 9700 — OAuth 2.0 Security BCP](https://datatracker.ietf.org/doc/html/rfc9700)
oraz [Spring Security — JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

**Sprawdź:** odróżnij authentication, scope i własność obiektu. Podpisany token
innego audience oraz prawidłowy token innego właściciela mają zostać odrzucone z różnych powodów.

## 10. Kafka i messaging

[Pytania](10-kafka-messaging.md). **Książka:** I5, log i przetwarzanie strumieni;
dodatkowo H4 z [bibliografii System Design](../system-design/MATERIALY.md#książki-przewodnie).
**Bezpłatnie:** [Apache Kafka 4.0 — design i delivery semantics](https://kafka.apache.org/40/design/design/)
oraz [Debezium — Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html).

**Sprawdź:** umieść crash przed i po zapisie DB oraz commicie offsetu.
Powiedz, w jakiej granicy działa exactly-once i kto usuwa podwójny efekt poza Kafką.

## 11. Systemy rozproszone i odporność

[Pytania](11-systemy-rozproszone.md). **Książka:** I5;
H3 z [bibliografii System Design](../system-design/MATERIALY.md#książki-przewodnie) dla failure modes.
**Bezpłatnie:** [AWS — timeouts, retries, jitter](https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/)
oraz [publikacja o Raft](https://raft.github.io/raft.pdf).

**Sprawdź:** pokaż, jak retry mnoży ruch i kiedy quorum chroni safety.
Timeout nie jest dowodem niewykonania operacji, a kompensacja nie jest rollbackiem całego świata.

## 12. Produkcja, cloud i testowanie

[Pytania](12-produkcja-cloud-testy.md). **Książki:** I9 oraz I3 dla diagnostyki.
**Bezpłatnie:** [SRE Workbook — alerty z SLO](https://sre.google/workbook/alerting-on-slos/),
[Kubernetes — lifecycle poda](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/)
i [JUnit 5.12.2](https://docs.junit.org/5.12.2/user-guide/).

**Sprawdź:** połącz objaw, hipotezę, pomiar i działanie. Wyjaśnij, co potwierdza
readiness, co zakończenie procesu i jak sprawdzisz krytyczny przepływ po restore.

## 13. System Design

[Pytania](13-system-design.md). **Książka:** I5 oraz H1 dla struktury rozmowy.
[Bibliografia 12 studiów](../system-design/MATERIALY.md) dobiera książki, protokoły,
dokumentację i pytania kontrolne do konkretnego systemu; bezpłatne pogłębienie
zapewnia [MIT 6.5840](https://pdos.csail.mit.edu/6.5840/).

**Sprawdź:** zacznij od access patterns, skali, niezmiennika i SLO. Przejdź
jeden krytyczny przepływ z awarią, zamiast odtwarzać zapamiętany diagram.

## 14. Płatności i bankowość

[Pytania](14-platnosci-bankowosc.md). **Książki:** I5 oraz H1, tom 2, payment/wallet.
**Bezpłatnie:** [Stripe — idempotencja](https://docs.stripe.com/api/idempotent_requests),
[webhooki](https://docs.stripe.com/webhooks)
oraz [TigerBeetle — financial accounting](https://docs.tigerbeetle.com/coding/financial-accounting/).

**Sprawdź:** rozróżnij autoryzację płatności, capture i zewnętrzne rozliczenie.
Pokaż podwójne zdarzenie oraz nieznany wynik PSP. Dokumentacja integracji nie
zastępuje lokalnych zasad rachunkowości, compliance i kontraktu banku/PSP.

## 15. Algorytmy i struktury danych

[Pytania](15-algorytmy.md). **Książka:** I8 lub L3 z [bibliografii LLD](../low-level-design/MATERIALY.md#książki-i-kolejność-pracy).
**Bezpłatnie:** Pat Morin, [Open Data Structures](https://opendatastructures.org/),
oraz Jeff Erickson, [Algorithms](https://jeffe.cs.illinois.edu/teaching/algorithms/).

**Sprawdź:** wyprowadź stan DP lub niezmiennik przejścia grafu. Podaj koszty
czasu i pamięci, przypadek brzegowy i prostą wyrocznię; kod z pamięci nie wystarcza.

## 16. Low Level Design

[Pytania](16-low-level-design.md). **Książki:** I1/I8; L5 dla struktur trwałych
w [bibliografii LLD](../low-level-design/MATERIALY.md).
**Bezpłatnie:** [API kolekcji JDK 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/package-summary.html)
oraz pełny podręcznik Morina wskazany w rozdziale 15.

**Sprawdź:** przypisz operacje do struktur, wskaż właściciela mutowalnego stanu
i efekt odmowy. Uzasadnij koszt utrzymania indeksów oraz kopii; nazwanie wzorca nie dowodzi projektu.

## 17. Doświadczenie, decyzje i AI

[Pytania](17-doswiadczenie-ai.md). **Książki:** I6 dla zmiany kodu, I9 dla incydentu,
I7 dla funkcji AI. **Bezpłatnie:** [Google — kultura postmortem](https://sre.google/workbook/postmortem-culture/)
oraz [OWASP — zagrożenia aplikacji LLM](https://owasp.org/www-project-top-10-for-large-language-model-applications/).

**Sprawdź:** oprzyj historię na rzeczywistym projekcie lub jasno nazwanym
eksperymencie. Dla RAG opisz kontrolę dostępu przy retrieval, zestaw ewaluacyjny,
koszt, deadline i zachowanie po awarii; udany przykład odpowiedzi nie jest miarą jakości.

## Aktualność i weryfikacja

Dobór i adresy źródeł sprawdzono 25.10.2026. Wersje są celowe: JDK 21, Spring
Framework 6.2, Hibernate ORM 6.6, Kafka 4.0 i JUnit 5.12.2 pomagają rozróżnić
kontrakt od hasła „najnowsze”. Nie oznacza to, że każde laboratorium używa
identycznego zestawu. Linki current/stable oraz niewersjonowane dokumentacje
mogą opisywać inne wydanie; porównuj je z wersją, o którą pytasz.

Źródło przy konkretnej odpowiedzi ma pierwszeństwo w ustalaniu jej kontekstu
wersji. Ta bibliografia nie zmienia treści ani numeracji pytań. Kompilację
oznaczonych przykładów i granice dowodów opisuje [instrukcja kontroli](checks/README.md).
