# Pytania i odpowiedzi — Java Backend Mid → Senior

Uporządkowany zbiór **323 pytań i odpowiedzi w 17 rozdziałach**, opracowany z dostarczonych notatek rekrutacyjnych. Pytania i odpowiedzi są po polsku; nazwy API i utrwalone terminy techniczne zachowują oryginalną pisownię. To moduł dokumentacyjny z przykładami Java, SQL, HTTP i konfiguracji, bez osobnej aplikacji i zależności.

Powtórzenia znaczeniowe mają jedną odpowiedź. Warianty zawierające dodatkowy warunek, np. `REQUIRES_NEW`, timeout po obciążeniu karty albo ograniczenie liczby przesiadek, zachowują ten warunek. Luźne notatki techniczne zostały przekształcone w pytania. Pominięto reklamy, wyniki rozmów i podpisy serwisów.

## Materiały do nauki

[Książki i źródła do powtórek](MATERIALY.md) dobierają lekturę, bezpłatne materiały
i zadanie sprawdzające do każdego z 17 rozdziałów. Rozróżniają starsze książki,
wersjonowane gwarancje API oraz dokumentację zmieniającą się wraz z narzędziem.

## Rozdziały

| Rozdział | Zakres |
| --- | --- |
| [01. Podstawy Javy](01-java.md) | JDK, obiekty, typy, wyjątki, Java 8/17/21 |
| [02. Kolekcje i Stream API](02-kolekcje-streamy.md) | HashMap, ConcurrentHashMap, iteratory, strumienie |
| [03. Współbieżność i model pamięci](03-wspolbieznosc.md) | happens-before, atomowość, publikacja, blokady |
| [04. Wykonanie asynchroniczne i virtual threads](04-async-virtual-threads.md) | executory, anulowanie, reactive, ograniczenia zasobów |
| [05. JVM i diagnostyka](05-jvm-diagnostyka.md) | GC, OOM, CPU, pamięć, JFR |
| [06. Spring i Spring Boot](06-spring-boot.md) | kontener, proxy, konfiguracja, lifecycle, MVC |
| [07. Transakcje i JPA](07-transakcje-jpa.md) | propagacja, rollback, dirty checking, blokady |
| [08. SQL i bazy danych](08-bazy-danych.md) | plany, indeksy, MVCC, batch, skalowanie |
| [09. API i bezpieczeństwo](09-api-security.md) | HTTP, OAuth2, JWT, kontrakty, kontrola dostępu |
| [10. Kafka i messaging](10-kafka-messaging.md) | partycje, konsumenci, offsety, duplikaty, outbox |
| [11. Systemy rozproszone i odporność](11-systemy-rozproszone.md) | saga, retry, cache, limity, replikacja |
| [12. Produkcja, cloud i testowanie](12-produkcja-cloud-testy.md) | incydenty, observability, Kubernetes, AWS, CI/CD |
| [13. System design](13-system-design.md) | przepływy, modele danych, skalowanie, awarie i kompromisy |
| [14. Płatności i bankowość](14-platnosci-bankowosc.md) | ledger, rozliczenia, fraud, audyt, przepływy płatnicze |
| [15. Algorytmy i struktury danych](15-algorytmy.md) | sposób rozwiązania, złożoność, przypadki brzegowe |
| [16. Low Level Design](16-low-level-design.md) | odpowiedzialności, niezmienniki i dobór struktur |
| [17. Doświadczenie, decyzje i AI](17-doswiadczenie-ai.md) | prawdziwe historie, kompromisy, LLM i RAG |

## Jak korzystać

Wyszukiwanie konkretnego zagadnienia ułatwia [indeks wszystkich pytań](INDEKS.md). Każdy rozdział ma lokalny spis z kotwicami i bezpośrednie odsyłacze do wybranych laboratoriów. Pod 34 odpowiedziami są pytania pogłębiające z rozwijanym kierunkiem odpowiedzi; nie są dodatkowymi kanonicznymi pytaniami w liczbie 323.

Zacznij od odpowiedzi własnymi słowami, potem porównaj mechanizm i ograniczenia. Odpowiedzi łączą definicję z celem, działaniem pod spodem, pułapką oraz zastosowaniem. Nie każde pytanie wymaga pięciu jednakowych podpunktów: przy algorytmie ważniejszy jest niezmiennik, przy incydencie dowód, przy architekturze granica spójności i skutki awarii.

System design rozwija przepływy, model danych, źródło prawdy, skalowanie i scenariusze awarii. Liczba użytkowników sama nie wyznacza infrastruktury: potrzebne są RPS, profil obciążenia, rozmiary danych i SLO. Przykładowe architektury są propozycjami projektowymi, a nie opisem wnętrza firm wymienionych w pytaniach.

Pytania o doświadczenie zawierają schemat uczciwej odpowiedzi, bez wymyślonych historii zawodowych. Przykłady kodu pokazują rdzeń mechanizmu; fragmenty zależne od domenowych typów, Springa lub konfiguracji są oznaczone jako szkice. Nie stanowią kompletnej aplikacji. Zadania algorytmiczne i LLD łączą rozumowanie z przykładami wykonania i wybranymi implementacjami. Pełne ćwiczenia są w [laboratoriach](../backend-engineering/README.md), [LLD](../low-level-design/README.md) i [studiach system design](../system-design/README.md).

## Wersje i weryfikacja

Przykłady językowe odnoszą się głównie do Javy 8, 17 i 21; różnice późniejszych wersji są oznaczone. Spring opisano przede wszystkim w domyślnym trybie proxy. Gwarancje zależne od providera, silnika bazy i konfiguracji są wskazane w odpowiedzi. Źródła przy szczegółach wersjonowanych sprawdzono 24.10.2026.

[Zasady scalania i pokrycie materiału](REDAKCJA.md) wyjaśniają, które warianty trafiły do wspólnych odpowiedzi i jakie błędne założenia skorygowano.

Każdy blok ma widoczny kontekst: fragment JDK kompilowany przez skrypt, szkic wymagający domeny/frameworka, szablon SQL, kontrakt HTTP lub materiał opisowy. Kompilowalne kontrprzykłady są oznaczone jako błędne logicznie. Materiał [diagnostyczny](DIAGNOSTYKA.md) zawiera syntetyczne plany SQL, thread dump i histogramy z wyjaśnieniem, czego dowodzą i czego nie rozstrzygają.

Z katalogu modułu uruchom `pwsh -File scripts/check-examples.ps1` (PowerShell 7 i JDK 21+ przez JAVA_HOME/PATH lub parametr JdkHome). Skrypt sprawdza konteksty i nawigację, kompiluje oznaczone fragmenty bezpośrednio z Markdown oraz uruchamia wybrane testy zachowania. Moduł nie ma buildu aplikacji ani zewnętrznych zależności testowych.

Z katalogu głównego repozytorium uruchom dodatkowo `pwsh -File scripts/check-documentation.ps1`, aby sprawdzić lokalne odsyłacze. Spisy po zmianie tytułów odświeża `scripts/update-navigation.ps1`; opcja -Check sprawdza je bez zapisu. Szczegółowy zakres, tryb DocumentsOnly i granice testów opisuje [instrukcja weryfikacji](checks/README.md). Szkice Spring/JPA/SQL nie są automatycznie uruchamiane na frameworku lub bazie.
