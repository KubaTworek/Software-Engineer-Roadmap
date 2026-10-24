# Książki i źródła do System Design

[Studia przypadku i sposób pracy](README.md)

Materiały służą uzasadnianiu decyzji w 12 niezależnych studiach. Wybierz jedną
lekturę przewodnią, przeczytaj część dotyczącą bieżącego problemu i wróć do kodu
oraz testu. Nie trzeba kupować ani czytać wszystkich książek przed rozpoczęciem.
Większość źródeł jest po angielsku. Książki komercyjne wymagają zakupu, biblioteki
lub subskrypcji; osobno wskazane materiały są dostępne bezpłatnie.

## Książki przewodnie

| ID | Książka i dostęp | Co wybrać z lektury |
| --- | --- | --- |
| H1 | Alex Xu; w tomie 2 także Sahn Lam, [System Design Interview — tomy 1 i 2](https://blog.bytebytego.com/p/system-design-interview-books-volume), komercyjne | Na początek: wymagania, estymacja i rozmowa o alternatywach. Tom 1: limiter, URL, powiadomienia, feed, chat, autocomplete, wideo i pliki. Tom 2: geolokalizacja, telemetryka, object storage i płatności. To przykładowe rozwiązania, nie specyfikacje firm ani dowód gwarancji. |
| H2 | Martin Kleppmann, Chris Riccomini, [Designing Data-Intensive Applications, wyd. 2](https://www.oreilly.com/library/view/designing-data-intensive-applications/9781098119058/), komercyjna | Główne pogłębienie: modele danych, replikacja, partycjonowanie, transakcje, przetwarzanie strumieni i dane pochodne. Uzasadnij granice spójności oraz odbudowę po awarii. |
| H3 | Michael Nygard, [Release It!, wyd. 2](https://store.pragprog.com/titles/mnee2/release-it-second-edition/), komercyjna | Timeouty, bulkhead, backpressure i kaskadowe awarie. Przełóż wybrany scenariusz na ograniczony budżet zasobów i eksperyment recovery. |
| H4 | Gregor Hohpe, Bobby Woolf, [Enterprise Integration Patterns](https://www.informit.com/store/enterprise-integration-patterns-designing-building-9780321200686), komercyjna; [katalog autorów](https://www.enterpriseintegrationpatterns.com/) bezpłatny | Routing, correlation, deduplikacja i kolejność komunikatów. Starsze technologie są tłem; książka nie definiuje gwarancji współczesnej Kafki ani transakcji Kafka–DB. |
| H5 | Alex Petrov, [Database Internals](https://www.databass.dev/), komercyjna | Dla pogłębienia: B-tree/LSM, WAL, recovery, replikacja i konsensus. Wykorzystaj do porównania storage; nie wymaga budowania własnej bazy w każdym studium. |
| H6 | Sam Newman, [Building Microservices, wyd. 2](https://samnewman.io/books/building_microservices_2nd_edition/), komercyjna | Granice danych i usług, wdrażanie oraz testowanie integracji. Oceniaj także koszt podziału i możliwość pozostania przy monolicie. |
| H7 | Google, [Site Reliability Engineering — wydanie z 2016 r.](https://sre.google/sre-book/table-of-contents/) oraz [The Site Reliability Workbook](https://sre.google/workbook/table-of-contents/), pełny tekst bezpłatny | SLI/SLO, budżet błędów, przeciążenie, incydenty i praktyczne alerty. Wybierz mierzalny sygnał oraz działanie operatora. Link do pierwszej książki celowo wskazuje starsze, dostępne online wydanie. |

Jeśli masz pierwsze DDIA, nadal wykorzystasz jego fundamenty. Tematy wyszukuj
po nazwie: numeracja rozdziałów różni się między wydaniami. H1 pomaga uporządkować
odpowiedź, H2–H7 pomagają sprawdzić jej założenia. Żaden diagram z książki nie
zastępuje uzasadnienia, że rozwiązanie chroni niezmiennik tego laboratorium.

## Bezpłatne pogłębienie przekrojowe

- [MIT 6.5840 — Distributed Systems](https://pdos.csail.mit.edu/6.5840/): wykłady,
  publikacje i laboratoria o replikacji oraz odporności. Ćwiczenia kursu używają
  Go i mają własne zasady; tutaj można najpierw analizować publikacje i historie awarii.
- Diego Ongaro, John Ousterhout, [In Search of an Understandable Consensus Algorithm](https://raft.github.io/raft.pdf):
  safety, wybór lidera i replikacja logu. Rozróżnij potwierdzenie przez quorum
  od samego wysłania wiadomości do repliki.
- [AWS Builders' Library — timeouts, retries and backoff with jitter](https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/):
  budżety czasu i narastanie obciążenia od ponowień.
- [Google SRE Workbook — Alerting on SLOs](https://sre.google/workbook/alerting-on-slos/):
  alert z tempa zużycia budżetu, a nie z pojedynczego błędu albo średniej latency.

## Chat System

[Studium](chat-system/README.md). **Lektura:** H1, chat; H2, kolejność i replikacja.
**Bezpłatnie:** [RFC 6455 — WebSocket](https://www.rfc-editor.org/rfc/rfc6455)
oraz [Matrix Client-Server API](https://spec.matrix.org/latest/client-server-api/),
zwłaszcza synchronizacja i presence. Matrix jest przykładem zewnętrznego protokołu;
laboratorium nie deklaruje jego implementacji.

**Zastosuj:** rozdziel ack transportu, trwały zapis i potwierdzenie odbiorcy.
Prześledź reconnect urządzenia oraz ponowienie tej samej wiadomości.

## E-commerce

[Studium](ecommerce/README.md). **Lektura:** H2, transakcje; H4, komunikaty; H6, granice danych.
**Bezpłatnie:** [Debezium Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html)
oraz [Stripe — idempotent requests](https://docs.stripe.com/api/idempotent_requests).
To kontrakty konkretnych narzędzi, nie wspólna transakcja inventory–PSP–order.

**Zastosuj:** opisz timeout po obciążeniu karty i crash po commicie outboxa.
Wskaż stabilny klucz, stan UNKNOWN i drogę rekoncyliacji.

## File Storage

[Studium](file-storage/README.md). **Lektura:** H1, pliki/object storage; H2, metadane i dane pochodne.
**Bezpłatnie:** [S3 multipart upload](https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html)
i [model spójności S3](https://aws.amazon.com/s3/consistency/).
Gwarancji S3 nie przenoś automatycznie na MinIO ani na transakcję obejmującą bazę metadanych.

**Zastosuj:** prześledź przerwany upload, finalizację i osierocony blob.
Ustal, kto sprząta części oraz kiedy plik staje się widoczny.

## Metrics i Logging

[Studium](metrics-logging-system/README.md). **Lektura:** H1, monitoring; H5, storage; H7, SLO.
**Bezpłatnie:** [Prometheus — nazwy i etykiety](https://prometheus.io/docs/practices/naming/)
oraz [OpenTelemetry Metrics Data Model](https://opentelemetry.io/docs/specs/otel/metrics/data-model/).

**Zastosuj:** oblicz koszt cardinality po dodaniu userId jako etykiety.
Rozróżnij counter, gauge i histogram, opóźniony punkt oraz brak próbki.

## News Feed

[Studium](news-feed/README.md). **Lektura:** H1, feed; H2, dane pochodne i strumienie.
**Bezpłatnie:** Raffi Krikorian, [Timelines at Scale](https://www.infoq.com/presentations/Twitter-Timeline-Scalability/),
nagranie wystąpienia inżyniera Twittera. To historyczne studium z 2013 r.,
nie opis dzisiejszej architektury X.

**Zastosuj:** porównaj koszt fan-out on write i on read dla zwykłego konta
oraz konta z wieloma obserwującymi. Dodaj opóźniony delete i odbudowę feedu.

## Notification System

[Studium](notification-system/README.md). **Lektura:** H1, powiadomienia; H3, odporność; H4, delivery.
**Bezpłatnie:** [FCM — lifecycle tokenów urządzeń](https://firebase.google.com/docs/cloud-messaging/manage-tokens)
oraz [AWS — retries i jitter](https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/).

**Zastosuj:** odróżnij przyjęcie przez providera od odczytania przez człowieka.
Rozważ nieaktualny token, limit providera i ponowienie po niejednoznacznym timeoutcie.

## Payment System

[Studium](payment-system/README.md). **Lektura:** H1, payment/wallet; H2, transakcje; H4, deduplikacja.
**Bezpłatnie:** [Stripe — webhooki](https://docs.stripe.com/webhooks),
[idempotencja](https://docs.stripe.com/api/idempotent_requests)
oraz [TigerBeetle — financial accounting](https://docs.tigerbeetle.com/coding/financial-accounting/).

**Zastosuj:** odtwórz podwójny webhook, refund i nieznany wynik PSP. Wyjaśnij
równowagę zapisów ledgera i uzgadnianie z zewnętrznym rozliczeniem.
Materiały techniczne nie zastępują wymagań księgowych ani regulacyjnych organizacji.

## Rate Limiter

[Studium](rate-limiter/README.md). **Lektura:** H1, limiter; H3, przeciążenie i degradacja.
**Bezpłatnie:** [Redis EVAL](https://redis.io/docs/latest/commands/eval/)
oraz [HTTP Semantics](https://datatracker.ietf.org/doc/html/rfc9110), m.in. Retry-After.

**Zastosuj:** pokaż, które sprawdzenie i zapis muszą być atomowe.
Oddziel atomowość skryptu na danym serwerze od trwałości przy failoverze
i opisz fail-open/fail-closed oraz ograniczenie liczby kluczy.

## Ride Sharing

[Studium](ride-sharing/README.md). **Lektura:** H1, proximity/maps; H2, partycjonowanie; H6, granice usług.
**Bezpłatnie:** [H3 — indeks przestrzenny](https://h3geo.org/docs/)
oraz [PostGIS ST_DWithin](https://postgis.net/docs/ST_DWithin.html).
To alternatywne narzędzia do porównania; ich obecność w bibliografii nie oznacza zależności projektu.

**Zastosuj:** rozdziel wybór kandydatów od dokładnego dystansu i atomowego
przypisania kierowcy. Sprawdź granicę komórki oraz opóźnioną pozycję GPS.

## Search Autocomplete

[Studium](search-autocomplete/README.md). **Lektura:** H1, autocomplete; H2, indeks jako dane pochodne.
**Bezpłatnie:** [Elasticsearch — suggesters](https://www.elastic.co/docs/reference/elasticsearch/rest-apis/search-suggesters)
oraz [Unicode UAX #15](https://www.unicode.org/reports/tr15/).
Dokumentacja Elasticsearch jest punktem porównania z modelem w pamięci.

**Zastosuj:** rozdziel candidate generation, ranking i moderację. Ustal kontrakt
normalizacji i sprawdź publikację nowej generacji indeksu bez mieszania wersji.

## URL Shortener

[Studium](url-shortener/README.md). **Lektura:** H1, URL i identyfikatory; H2, hot keys i partycjonowanie.
**Bezpłatnie:** [RFC 9110 — HTTP Semantics](https://datatracker.ietf.org/doc/html/rfc9110)
oraz [RFC 9111 — HTTP Caching](https://www.rfc-editor.org/rfc/rfc9111).

**Zastosuj:** uzasadnij kod redirectu, cache i możliwość odwołania linku.
Nie utożsamiaj bezkolizyjnego wygenerowania kandydata z unikalnym zapisem w bazie.

## Video Streaming

[Studium](video-streaming/README.md). **Lektura:** H1, wideo; H3, przeciążenie; H2, asynchroniczny pipeline.
**Bezpłatnie:** [RFC 8216 — HLS](https://www.rfc-editor.org/rfc/rfc8216)
oraz [dokumentacja FFmpeg](https://ffmpeg.org/ffmpeg.html).
RFC opisuje określoną wersję protokołu; rozszerzenia, np. low-latency HLS,
wymagają dodatkowej dokumentacji.

**Zastosuj:** rozdziel transkodowanie, packaging i odtwarzanie. Wyjaśnij,
kiedy playlistę można opublikować, jak ponowić zadanie i co naprawdę cache'uje CDN.

## Aktualność i granice źródeł

Dobór i adresy źródeł sprawdzono 25.10.2026 na stronach autorów, wydawców,
organizacji standaryzacyjnych i dokumentacji narzędzi. Linki latest/stable
zmieniają zawartość: API i konfigurację porównuj z wersją z pom.xml/Compose
wybranego studium. Także dokumentacja konkretnego dostawcy opisuje jego warunki,
a nie gwarancje wszystkich produktów o podobnym API.

Po lekturze zapisz wymaganie, decyzję, odrzuconą alternatywę, niezmiennik i test
awarii. Nowe komponenty z książki pozostają propozycją, dopóki nie ma ich w kodzie
oraz mapie implementacji. Nie rozszerzaj diagramu tylko dla zgodności z lekturą.
