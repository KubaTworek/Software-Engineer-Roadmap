# System design — przepływy, dane i awarie

[Spis rozdziałów](README.md) · [Książki i źródła](MATERIALY.md#13-system-design)

Każda odpowiedź proponuje architekturę dla zadanych potrzeb. Nazwy produktów oznaczają typ problemu, nie znajomość ich prywatnej implementacji. Projekty płatnicze i kredytowe są w [osobnym rozdziale](14-platnosci-bankowosc.md).

<!-- question-toc:start -->
## Spis pytań

- [SD-01. Jak zaprojektować platformę Java/Spring Boot na Kubernetes i AWS dla 10 mln użytkowników?](#sd-01-jak-zaprojektować-platformę-javaspring-boot-na-kubernetes-i-aws-dla-10-mln-użytkowników)
- [SD-02. Jak zaprojektować URL shortener z aliasami, analityką i miliardem przekierowań dziennie?](#sd-02-jak-zaprojektować-url-shortener-z-aliasami-analityką-i-miliardem-przekierowań-dziennie)
- [SD-03. Jak zaprojektować powiadomienia e-mail, SMS i push z retry, offline i alertami fraud?](#sd-03-jak-zaprojektować-powiadomienia-e-mail-sms-i-push-z-retry-offline-i-alertami-fraud)
- [SD-04. Jak zaprojektować news feed z rankingiem i popularnymi użytkownikami?](#sd-04-jak-zaprojektować-news-feed-z-rankingiem-i-popularnymi-użytkownikami)
- [SD-05. Jak zaprojektować rozproszony cache działający w wielu regionach?](#sd-05-jak-zaprojektować-rozproszony-cache-działający-w-wielu-regionach)
- [SD-06. Jak zaprojektować rate limiter dla wielu gatewayów i grup klientów?](#sd-06-jak-zaprojektować-rate-limiter-dla-wielu-gatewayów-i-grup-klientów)
- [SD-07. Jak zaprojektować skalowalny API Gateway?](#sd-07-jak-zaprojektować-skalowalny-api-gateway)
- [SD-08. Jak zaprojektować e-commerce i checkout z rezerwacją zapasu i idempotentną płatnością?](#sd-08-jak-zaprojektować-e-commerce-i-checkout-z-rezerwacją-zapasu-i-idempotentną-płatnością)
- [SD-09. Jak zaprojektować mikroserwisowy Order Management System?](#sd-09-jak-zaprojektować-mikroserwisowy-order-management-system)
- [SD-10. Jak zaprojektować chat z kolejnością, potwierdzeniami i synchronizacją offline?](#sd-10-jak-zaprojektować-chat-z-kolejnością-potwierdzeniami-i-synchronizacją-offline)
- [SD-11. Jak zaprojektować globalny video streaming z adaptive bitrate?](#sd-11-jak-zaprojektować-globalny-video-streaming-z-adaptive-bitrate)
- [SD-12. Jak zbudować wyszukiwanie produktów i indeksowanie miliarda dokumentów z szybkimi aktualizacjami?](#sd-12-jak-zbudować-wyszukiwanie-produktów-i-indeksowanie-miliarda-dokumentów-z-szybkimi-aktualizacjami)
- [SD-13. Jak zaprojektować autocomplete/typeahead?](#sd-13-jak-zaprojektować-autocompletetypeahead)
- [SD-14. Jak zaprojektować storage plików z wersjonowaniem, deduplikacją i konfliktami?](#sd-14-jak-zaprojektować-storage-plików-z-wersjonowaniem-deduplikacją-i-konfliktami)
- [SD-15. Jak zaprojektować ride-hailing, matching kierowców i surge pricing?](#sd-15-jak-zaprojektować-ride-hailing-matching-kierowców-i-surge-pricing)
- [SD-16. Jak zaprojektować food delivery typu Uber Eats?](#sd-16-jak-zaprojektować-food-delivery-typu-uber-eats)
- [SD-17. Jak śledzić dostawy w czasie rzeczywistym dla milionów zamówień?](#sd-17-jak-śledzić-dostawy-w-czasie-rzeczywistym-dla-milionów-zamówień)
- [SD-18. Jak zaprojektować rezerwacje biletów lub noclegów bez double-bookingu?](#sd-18-jak-zaprojektować-rezerwacje-biletów-lub-noclegów-bez-double-bookingu)
- [SD-19. Jak zaprojektować scentralizowane logowanie o skali petabajtów?](#sd-19-jak-zaprojektować-scentralizowane-logowanie-o-skali-petabajtów)
- [SD-20. Jak zaprojektować monitoring i alerting z kontrolą kardynalności?](#sd-20-jak-zaprojektować-monitoring-i-alerting-z-kontrolą-kardynalności)
- [SD-21. Jak zaprojektować edytor współdzielony typu Google Docs?](#sd-21-jak-zaprojektować-edytor-współdzielony-typu-google-docs)
- [SD-22. Jak zaprojektować rozproszony scheduler z retry i „exactly-once execution”?](#sd-22-jak-zaprojektować-rozproszony-scheduler-z-retry-i-exactly-once-execution)
- [SD-23. Jak zaprojektować workflow engine wykonujący DAG?](#sd-23-jak-zaprojektować-workflow-engine-wykonujący-dag)
- [SD-24. Jak zaprojektować reklamy i bidding poniżej 100 ms z kontrolą budżetu?](#sd-24-jak-zaprojektować-reklamy-i-bidding-poniżej-100-ms-z-kontrolą-budżetu)
- [SD-25. Jak zaprojektować IoT ingestion dla milionów niestabilnych połączeń?](#sd-25-jak-zaprojektować-iot-ingestion-dla-milionów-niestabilnych-połączeń)
- [SD-26. Jak zaprojektować backend gry multiplayer z synchronizacją i lag compensation?](#sd-26-jak-zaprojektować-backend-gry-multiplayer-z-synchronizacją-i-lag-compensation)
- [SD-27. Jak zaprojektować rekomendacje z sygnałami realtime i modelami batch?](#sd-27-jak-zaprojektować-rekomendacje-z-sygnałami-realtime-i-modelami-batch)
- [SD-28. Jak zaprojektować CDN i wybór edge oraz invalidację?](#sd-28-jak-zaprojektować-cdn-i-wybór-edge-oraz-invalidację)
- [SD-29. Jak zaprojektować web crawler?](#sd-29-jak-zaprojektować-web-crawler)
- [SD-30. Jak zaprojektować trwały rozproszony key-value store?](#sd-30-jak-zaprojektować-trwały-rozproszony-key-value-store)
- [SD-31. Jak zaprojektować rozproszoną kolejkę zadań typu Kafka?](#sd-31-jak-zaprojektować-rozproszoną-kolejkę-zadań-typu-kafka)
- [SD-32. Jak zaprojektować rozproszony generator ID?](#sd-32-jak-zaprojektować-rozproszony-generator-id)
<!-- question-toc:end -->

## SD-01. Jak zaprojektować platformę Java/Spring Boot na Kubernetes i AWS dla 10 mln użytkowników?

Najpierw ustalam aktywnych użytkowników, peak RPS, operacje, payload, retencję i SLO. Punkt startowy: CDN/WAF → LB/gateway → stateless API w kilku strefach → transakcyjna DB, z Redis dla wybranych odczytów i trwałym brokerem dla pracy odroczonej. Kubernetes/EKS jest opcją operacyjną, nie wymogiem wynikającym z liczby użytkowników.

Skaluję po zmierzonym bottlenecku, kontroluję sumę pul i admission. Niezmienniki chroni DB, eventy wychodzą przez outbox, konsumenci deduplikują. Tracing, SLO, backup/restore i kontrolowany rollout weryfikują działanie. Każdy dodatkowy komponent musi rozwiązywać konkretny problem i mieć właściciela.

Przykładowy punkt startowy dla platformy zamówień:

<!-- example:SD-01-1 kind:mermaid-diagram -->
**Kontekst:** Diagram Mermaid opisujący model; nie dowodzi wykonania ani gotowości wdrożenia.

~~~mermaid
flowchart LR
    U[Klient] --> G[Gateway / LB]
    G --> A[API stateless]
    A --> D[(DB: zamówienia + outbox)]
    A --> C[(Cache odczytu)]
    D --> R[Relay / CDC]
    R --> B[Broker]
    B --> W[Workerzy integracji]
~~~

Każda strzałka ma timeout, limit i właściciela błędu. Jeżeli użytkownik czeka na przyjęcie zamówienia, odpowiedź może nastąpić po lokalnym commicie, a nie po wysłaniu emaila. Trzymanie wszystkiego na ścieżce synchronicznej mnoży zależności dostępności. Dane i flow skaluję oddzielnie: więcej API nie zwiększy przepustowości jednego gorącego rekordu magazynowego.

## SD-02. Jak zaprojektować URL shortener z aliasami, analityką i miliardem przekierowań dziennie?

Tworzenie zapisuje unikalny krótki klucz → docelowy URL, właściciela i ważność. Custom alias ma unique constraint i politykę rezerwacji nazw. Redirect czyta cache, potem trwały key-value/DB i zwraca odpowiednie 3xx; miliard/dobę to około 11,6 tys. odczytów/s średnio, z osobnym założeniem szczytu.

Popularne klucze obsługuje cache/edge; analitykę wysyłam asynchronicznie, żeby nie opóźniała redirectu. Cache musi respektować zmiany, blokady nadużyć i wygaśnięcie. Trwałe 301/308 utrudnia szybkie odwołanie przez cache klienta; 302/307 daje inne kompromisy. Generator ID nie może tworzyć kolizji.

Minimalny model obejmuje link(code PK, target, owner, expiresAt, version) oraz asynchroniczne zdarzenie redirectu. Unikalność aliasu rozstrzyga DB, nie odczyt „czy wolny” wykonany przed insertem. Alias nie powinien móc podszyć się pod zarezerwowane ścieżki aplikacji.

Przy GET /x7Q waliduję blokadę/wygaśnięcie według wymaganego kontraktu i zwracam redirect. Analitykę można buforować i agregować, ale jej strata/duplikaty muszą być jawnie dopuszczone. Popularny link tworzy hot key, więc shardowanie po code nie wystarczy samo. Cache edge pomaga, kosztem czasu potrzebnego do odwołania złośliwego URL.

## SD-03. Jak zaprojektować powiadomienia e-mail, SMS i push z retry, offline i alertami fraud?

API zapisuje trwałe zadanie z idempotency key, odbiorcą, priorytetem i preferencjami. Kolejki/partycje rozdzielają kanały, workerzy wywołują dostawców, a callbacki aktualizują status. Krytyczne alerty mają osobny budżet i krótki SLO, żeby marketing nie blokował ostrzeżeń.

Retry ma backoff, termin ważności i deduplikację; tokeny urządzeń są aktualizowane po błędzie. Przyjęcie przez dostawcę nie dowodzi odczytania ani dostarczenia na wyłączony telefon. Rozróżniam zaakceptowano, wysłano, dostarczono i przeczytano. Przy awarii dostawcy możliwy jest kontrolowany fallback z ochroną przed duplikatami.

Zadanie powiadomienia ma własny stan, np. ACCEPTED → SENDING → PROVIDER_ACCEPTED → DELIVERED albo EXPIRED. Provider message ID mapuje callback do tego samego zadania. Jedna wiadomość biznesowa może mieć kilka prób, ale nie powinna przypadkowo tworzyć kilku niezależnych alertów.

Dla push offline trzymam ważność: alarm sprzed godziny może nadal być potrzebny, a jednorazowy kod już nie. Failover SMS po timeoutcie pierwszego dostawcy może dać duplikat, jeśli pierwszy jednak wysłał. Kontrakt „w sekundach” wymaga priorytetowej ścieżki i pomiaru end-to-end; nie można zagwarantować odczytu na wyłączonym telefonie.

## SD-04. Jak zaprojektować news feed z rankingiem i popularnymi użytkownikami?

Posty są źródłem prawdy, a timeline projekcją. Dla zwykłych autorów fan-out on write zapisuje ID postów do feedów obserwujących; dla celebrytów fan-out on read ogranicza ogromny koszt zapisu. Przy odczycie łączę kandydatów, filtruję uprawnienia i rankuję z limitem czasu.

Cursor stabilizuje paginację, a model rankingu ma wersję. Eventual consistency jest zwykle akceptowalna dla kolejności, ale usunięcie i prywatność wymagają skutecznego filtrowania. Mierzę freshness, koszt fan-out i latency; po awarii rankingu używam prostszego, jawnego porządku.

Model obejmuje post, relację follow i wpis timeline(userId, postId, scoreVersion). Fan-out workerzy partycjonują odbiorców, a cursor odczytu określa stabilne miejsce i wersję rankingu. Nie musi dawać idealnie zamrożonej kolejności podczas dynamicznego przeliczania — trzeba to ustalić.

Celebryta z milionami obserwujących może zdominować pojedynczą publikację; wariant hybrydowy łączy gotowy feed z jego świeżymi postami przy odczycie. Usunięcie treści i cofnięcie dostępu egzekwuję również przy serwowaniu, bo nie każda kopia timeline zniknie natychmiast. Rekomendacje i ranking nie mogą przywrócić zabronionego postu ze starego cache.

## SD-05. Jak zaprojektować rozproszony cache działający w wielu regionach?

Klucz zawiera tenant i wersję semantyki; routing dzieli dane na shardowane węzły z replikami. Regionalny cache obsługuje lokalne odczyty, a zmiany źródła prawdy prowadzą do wersjonowanych aktualizacji/invalidacji. Definiuję dopuszczalną nieświeżość i zachowanie przy partition.

Silna spójność globalna wymaga koordynacji i kosztuje latency/dostępność; nie obiecuję jej jednocześnie z dowolnym lokalnym zapisem offline. Przy failover chronię DB przed stampede, kontroluję pamięć/TTL i odbudowę. Cache autorytatywny wymaga innego protokołu trwałości niż odtwarzalna kopia.

Klucz może zawierać region/tenant/schema-version, a wartość wersję źródłowego rekordu i termin ważności. Replikacja cache między regionami nie oznacza natychmiastowego widoku zmian. Przy asynchronicznym fan-out dwa regiony mogą przez chwilę zwracać różne kopie.

Dla danych bezpiecznie odtwarzalnych preferuję lokalne odczyty i jawną granicę świeżości. Dla autorytatywnego licznika globalnego potrzebny jest protokół mocniejszy niż zwykły Redis cache. Przy awarii regionu nowy region ma zimny cache, dlatego failover musi ograniczyć odczyty DB i stopniowo odbudowywać gorące klucze. Sprawdzam koszt transferu i opóźnienie invalidacji.

## SD-06. Jak zaprojektować rate limiter dla wielu gatewayów i grup klientów?

Token bucket per tenant/użytkownik/endpoint wykonuję atomowo we współdzielonym magazynie lub przydzielam ograniczone lokalne budżety gatewayom. Wymagany koszt operacji może konsumować różną liczbę tokenów. Osobne pule dla klientów detalicznych, firm i usług wewnętrznych zapobiegają zagłodzeniu.

Ustalam burst, czas i ryzyko przekroczenia podczas awarii koordynatora. Fail-open zwiększa ryzyko przeciążenia, fail-closed blokuje klientów; decyzja może być różna per operacja. Lokalny awaryjny limit i Retry-After ograniczają szkody. Limit RPS uzupełniam limitem pracy w toku.

Przy 20 gatewayach lokalny limit 100/s daje potencjalnie 2000/s. Dla globalnego 100/s można wykonywać atomową decyzję w koordynatorze lub przydzielać małe porcje tokenów instancjom. Drugi wariant obniża latency, lecz wymaga ograniczenia sumy wydanych budżetów i obsługi niewykorzystanych porcji po crashu.

Dla bankowego API wydzielam limity według tożsamości klienta, kosztu operacji i klasy ruchu. Jeden agresywny tenant nie powinien zużyć całego budżetu rozliczeń. Polityka awarii jest per operacja: otwarty odczyt informacyjny i zlecenie wypłaty mogą wymagać innego fail-open/fail-closed.

## SD-07. Jak zaprojektować skalowalny API Gateway?

Bezstanowe repliki za LB terminują/obsługują TLS zgodnie z architekturą, weryfikują credential, rozliczają quota i routują do odkrytych instancji. Konfiguracja tras i polityk jest wersjonowana oraz rozprowadzana bez zatrzymania ruchu. Dane użytkownika nie wymagają sesji w pamięci jednego gatewaya.

Ograniczam body, bufory, połączenia i złożoność transformacji; domain logic pozostaje w usługach. Control plane nie powinien być synchroniczną zależnością każdego requestu. Awaria auth/discovery ma zdefiniowany tryb działania, a retry nie mnoży prób serwisów. Obserwuję latency i błędy per upstream.

Gateway ma data plane obsługujący requesty i control plane dostarczający trasy/polityki. Znana poprawna konfiguracja może być lokalnie cache'owana, aby chwilowy brak control plane nie zatrzymał każdego requestu. Zmianę konfiguracji waliduję i wdrażam stopniowo tak jak kod.

Przykład: autoryzacja tokena, limit klienta i wybór upstreamu poprzedzają wywołanie; transformacja dużego body ma limit pamięci. Nie przenoszę do gatewaya zapytań domenowych, które tworzą kolejny monolit logiki. Mierzę osobno narzut gatewaya, upstream time, acquisition i timeouty. Prawidłowe forwardowanie kontekstu wymaga usuwania niezaufanych nagłówków.

## SD-08. Jak zaprojektować e-commerce i checkout z rezerwacją zapasu i idempotentną płatnością?

Katalog/search są projekcjami odczytu, a zamówienie zapisuje snapshot pozycji, ceny i waluty. Checkout tworzy PENDING, atomowo rezerwuje zapas, inicjuje autoryzację płatności z idempotency key i finalizuje zamówienie. To proces z lokalnymi transakcjami i trwałymi stanami.

Rezerwacja ma termin i bezpieczne zwolnienie; timeout operatora daje wynik nieznany, nie automatyczne ponowne obciążenie. Saga/outbox i rekoncyliacja obsługują crash między krokami. Popularny produkt wymaga ochrony przed overselling i admission. Nie blokuję wiersza zapasu przez całe zdalne wywołanie.

Model rezerwacji odróżnia stock dostępny od hold i sprzedanej ilości. Order zapisuje uzgodnioną ofertę, aby późniejsza zmiana katalogu nie zmieniła pobranej kwoty. Proces może mieć stany:

<!-- example:SD-08-1 kind:text-model -->
**Kontekst:** Model lub przebieg operacji zapisany tekstowo; nie jest programem do uruchomienia.

~~~text
PENDING → STOCK_HELD → PAYMENT_AUTHORIZED → CONFIRMED
                  ↘ EXPIRED / PAYMENT_FAILED → RELEASED
~~~

To szkic; spóźnione potwierdzenie wymaga osobnej decyzji. Unikalny operation ID wiąże retry klienta z tym samym checkoutem. Jeżeli płatność trwa dłużej niż hold, system nie może jednocześnie sprzedać towaru komuś innemu i potwierdzić starego zamówienia. Recovery sprawdza oba źródła, nie tylko ostatni event.

<!-- follow-up:SD-08 -->
**Sprawdź rozumienie:** Operator potwierdził płatność po wygaśnięciu holda, a towar sprzedano innemu klientowi. Czy wystarczy ponowić finalizację?

<details>
<summary>Kierunek odpowiedzi</summary>

Nie. Proces musi rozstrzygnąć spóźniony success: zwrot, alternatywa lub interwencja zgodnie z regułami. Nie wolno ominąć ochrony stock. Stabilne ID i trwała maszyna stanów wiążą odpowiedzi operatora z pierwotnym zamówieniem.

</details>

## SD-09. Jak zaprojektować mikroserwisowy Order Management System?

Usługa zamówień jest właścicielem maszyny stanów, a płatności, magazyn i wysyłka mają własne dane i kontrakty. Każde przejście stanu waliduje dozwolony poprzednik i wersję. Zdarzenia z outbox aktualizują projekcję klienta i uruchamiają dalsze kroki.

Duplikaty, opóźnienia i przyjście w złej kolejności są normalnymi wejściami. Anulowanie po wysyłce wymaga zwrotu, nie cofnięcia statusu. Orkiestrator lub jawnie udokumentowana choreografia utrzymuje timeouty i kompensacje. Mierzę wiek zamówień w stanach przejściowych.

Przejście stanu można chronić atomowym warunkiem:

<!-- example:SD-09-1 kind:sql-template -->
**Kontekst:** Szablon SQL — wymaga bazy, schematu i podstawienia parametrów; skrypt go nie wykonuje.

~~~sql
UPDATE orders
SET status = 'CONFIRMED', version = version + 1
WHERE id = :id AND status = 'PAYMENT_AUTHORIZED' AND version = :version;
~~~

To fragment modelu; wynik aktualizacji musi zostać sprawdzony i powiązany z outbox. Duplicate confirmation może zwrócić już znany stan, ale sprzeczne polecenie nie powinno być uznane za sukces. Oddzielam stan procesu od historii zdarzeń i od projekcji listy zamówień. Dashboard age-by-state wykrywa procesy, które nie zakończyły się mimo poprawnego HTTP.

## SD-10. Jak zaprojektować chat z kolejnością, potwierdzeniami i synchronizacją offline?

Gateway WebSocket uwierzytelnia połączenie, a serwis wiadomości zapisuje je z client message ID i numerem sekwencji w rozmowie. Dopiero trwały zapis daje potwierdzenie przyjęcia. Osobny routing obecności kieruje dostawę do aktywnych urządzeń; offline klient dogania od kursora.

Odróżniam sent, delivered i read, obsługuję duplikaty oraz wiele urządzeń. Backpressure odłącza lub spowalnia wolnych odbiorców. Sharding po rozmowie ułatwia porządek, ale wielkie grupy tworzą hotspot. Reconnect z jitter i trwała historia zapobiegają uzależnieniu od jednego socketu.

Dane obejmują conversationId, server sequence, sender, clientMessageId i treść/version. Unique(conversationId, sender, clientMessageId) może rozstrzygać ponowne wysłanie po reconnect. Porządek w rozmowie jest ważniejszy niż globalne timestampy wszystkich wiadomości.

Gateway potwierdza trwałe przyjęcie, a klient może osobno zgłosić odbiór i odczyt. Utrata socketu między zapisem a ack prowadzi do retry tego samego ID, nie nowej wiadomości. Dla bardzo dużych grup fan-out jest własnym pipeline z limitem wolnych odbiorców. Ewentualne szyfrowanie end-to-end zmienia możliwości serwerowego search/moderation i musi być osobnym wymaganiem.

## SD-11. Jak zaprojektować globalny video streaming z adaptive bitrate?

Upload trafia do object storage, a trwały pipeline koduje warianty jakości i tworzy segmenty oraz manifesty HLS/DASH. Odtwarzacz pobiera przez CDN i dobiera bitrate do przepustowości oraz bufora. Metadane, uprawnienia i media mają oddzielne ścieżki.

Kodowanie jest idempotentnym jobem z wersją profilu; niepublikowany materiał nie powinien pojawić się po połowicznym wykonaniu. CDN osłania origin, popularne treści można prewarmować. Mierzę startup delay, rebuffering i błędy segmentów; prawa dostępu egzekwuję także przy wydawaniu URL/tokenów.

Manifest nie powinien wskazywać częściowo wygenerowanego zestawu segmentów jako pełnego filmu. Pipeline tworzy artefakty pod wersją/generacją, waliduje kompletność i dopiero potem publikuje gotowy manifest. Retry transkodowania nie może usunąć działającej starszej generacji.

Klient mierzy bandwidth i bufor, wybierając wariant bitrate; origin nie obsługuje każdego odtworzenia wprost. CDN wymaga cache keys i terminów ważności zgodnych z uprawnieniami. Awaria jednej jakości może pozwolić zejść niżej, ale utrata metadanych lub DRM ma inne skutki. Jakość oceniam przez start, stall i ukończenie odtworzenia, nie tylko RPS.

## SD-12. Jak zbudować wyszukiwanie produktów i indeksowanie miliarda dokumentów z szybkimi aktualizacjami?

Źródło prawdy publikuje zmiany przez CDC/outbox do partycjonowanego pipeline. Normalizacja, tokenizacja i inverted index umożliwiają szybkie wyszukiwanie; shardowanie i repliki skalują zapis/odczyt. Bulk indexing, backpressure i kontrola merge ograniczają koszt dużego importu.

Near-real-time oznacza opóźnienie widoczności. Wersja dokumentu i tombstone chronią przed starszą aktualizacją po usunięciu; nowy indeks buduję obok starego i przełączam alias po walidacji. Search nie zastępuje transakcyjnej walidacji ceny/zapasu w checkout.

Pipeline musi rozróżniać wersję dokumentu i wersję indeksu. Starszy event nie powinien nadpisać nowszej treści, a delete potrzebuje tombstone chroniącego przed opóźnionym insert. Bulk rebuild czyta snapshot i dogania zmiany, zanim alias wskaże nową generację.

Shardy dobieram do rozmiaru indeksu, zapytań i merge cost, nie tylko liczby dokumentów. Zapytanie full-text ma ranking i relevance, a filtr tenant/uprawnienia ogranicza kandydatów. Index lag jest mierzoną częścią kontraktu. Dla miliarda rekordów liczę także storage replik, czas odbudowy oraz budżet przepustowości ingestion, bo awaria wymaga nadrobienia zaległości.

## SD-13. Jak zaprojektować autocomplete/typeahead?

Buduję indeks prefiksów, np. trie/FST albo indeks wyszukiwarki, z ograniczoną listą najlepiej ocenionych propozycji. Normalizacja obejmuje język i sposób liczenia znaków; ranking może łączyć popularność, świeżość i kontekst. Request ma krótki deadline i limit długości.

Popularne prefiksy są cache'owane, a zmiany indeksu publikowane atomowo lub wersjonowane. Filtruję niedozwolone propozycje i nie ujawniam prywatnych zapytań. Pełna personalizacja zwiększa koszt/cache cardinality; bez modelu mogę wrócić do ogólnych popularnych wyników.

Trie przechowuje ścieżki znaków i może utrzymywać top propozycje przy prefiksie. Wersja statyczna jest szybka w odczycie, ale aktualizacja rankingów wielu węzłów kosztuje; FST kompresuje strukturę kosztem innego modelu budowania. Indeks wyszukiwarki ułatwia języki i dodatkowe filtry.

Dla prefiksu „ja” nie pobieram wszystkich milionów wyników do sortowania w API. Serwer zwraca ograniczoną listę, a klient debounce i cancel poprzedniej próby zmniejsza ruch. Nie ufam wyłącznie temu cancel: stare odpowiedzi mogą przyjść później, więc UI potrzebuje tożsamości aktualnego zapytania.

## SD-14. Jak zaprojektować storage plików z wersjonowaniem, deduplikacją i konfliktami?

Metadane i uprawnienia są w DB, bajty/chunki w object storage. Klient wysyła części z checksum, a zakończenie uploadu atomowo publikuje manifest wersji. Zmiana pliku tworzy nową wersję z warunkiem oczekiwanej poprzedniej, zamiast nadpisywać cudzą edycję.

Deduplikacja oparta na hashach wymaga weryfikacji i kontroli izolacji tenantów; nie wolno przez API ujawniać istnienia cudzego pliku. Niedokończone uploady i osierocone chunki sprzątam bez usuwania nadal używanych. Konflikt offline zachowuję jako wersję lub rozwiązuję jawną polityką.

Upload ma stany INITIATED → PARTS_UPLOADED → VERIFIED → PUBLISHED. Metadane przechowują manifest chunków, checksum i ID wersji; klient nie widzi pliku jako gotowego po samym utworzeniu sesji. Brakujące części można ponowić bez przesyłania całości.

Deduplikacja potrzebuje atomowego zarządzania referencjami lub bezpiecznej garbage collection. Crash między utworzeniem chunka a manifestem może zostawić sierotę, natomiast zbyt szybki cleanup usunąć dane używane przez inną wersję. Konflikt edycji sprawdzam po oczekiwanym version ID, nie tylko nazwie pliku. Sharing/ACL i odzyskanie wersji są odrębnymi operacjami.

## SD-15. Jak zaprojektować ride-hailing, matching kierowców i surge pricing?

Pozycje z czasem/sekwencją trafiają do krótkotrwałego indeksu geograficznego, a trwałe przejazdy do transakcyjnego magazynu. Dopasowanie najpierw wybiera komórki geohash/H3, potem ocenia ETA i atomowo rezerwuje dostępnego kierowcę dla jednego przejazdu.

Geohash jest filtrem, nie dowodem najkrótszego czasu dojazdu; obsługuję sąsiednie komórki i stare lokalizacje. Oferty mają TTL, przyjęcie jest idempotentne. Surge jest wersjonowaną ofertą cenową, a nie późniejszą zmianą uzgodnionej ceny. Lokalny matching ogranicza opóźnienia i zakres awarii.

Pozycja kierowcy jest ulotnym sygnałem, stan przejazdu trwałą decyzją. Matching bierze kandydatów z sąsiednich komórek i odrzuca punkty zbyt stare, następnie atomowo próbuje claim dostępnego kierowcy. Dwóch dispatcherów może ocenić go równocześnie, ale tylko jeden powinien wygrać.

Oferta ma czas ważności i identyfikator, więc spóźnione przyjęcie nie może przejąć już zajętego kierowcy. Surge dotyczy wersji oferty zaakceptowanej przez pasażera. W regionie bez map stosuję ograniczony fallback lub kontrolowaną odmowę, zamiast generować losową cenę i ETA. Mierzę skuteczność dopasowań, nie tylko szybkość wyszukania geo.

## SD-16. Jak zaprojektować food delivery typu Uber Eats?

Rozdzielam menu/restauracje, zamówienie, płatność, przygotowanie i dostawę. Zamówienie utrwala zaakceptowaną cenę i dostępność, a saga koordynuje potwierdzenie restauracji oraz przydział kuriera. SQL chroni transakcyjne reguły, a search i geo projection wspierają odkrywanie oraz matching.

Kurier może przewozić kilka zamówień, więc optymalizacja uwzględnia czas gotowości i trasę, nie tylko odległość. Odmowa restauracji lub brak kuriera ma jawną politykę anulowania/zwrotu. Integracje są idempotentne; niedostępność map nie może zatrzymać istniejącego księgowania.

Restauracja może potwierdzić zamówienie dopiero po czasie, a kurier może je odrzucić. Proces potrzebuje terminów dla obu decyzji i jasnej polityki, kto ponosi koszt anulowania po rozpoczęciu przygotowania. To nie jest zwykły „checkout plus tabela drivers”.

Menu i dostępność mogą być projekcjami, ale potwierdzona pozycja zawiera snapshot ceny, opcji i terminu. Przy wspólnej trasie kilku dostaw optymalizacja uwzględnia czas gotowości jedzenia oraz obiecaną dostawę. Replay eventu nie może drugi raz uruchomić przygotowania ani capture płatności. Po utracie kuriera trwały proces szuka zastępstwa według reguł.

## SD-17. Jak śledzić dostawy w czasie rzeczywistym dla milionów zamówień?

Aplikacja kuriera publikuje pozycje z ID, sekwencją i czasem; ingestion uwierzytelnia, ogranicza tempo i zapisuje najnowszy stan. Broker rozprowadza aktualizacje do gatewayów klienta przez WebSocket/SSE. Trwała historia może mieć niższą częstotliwość niż bieżący podgląd.

Odrzucam stare punkty, oznaczam nieaktualną lokalizację i agreguję nadmiar, zamiast wysyłać każdą próbkę każdemu odbiorcy. Offline klient wznawia od znanego stanu. Dostęp do lokalizacji ograniczam do uprawnionego zamówienia i okresu retencji.

Przykładowe zdarzenie lokalizacji zawiera courierId, sequence, measuredAt, receivedAt, coordinates i accuracy. Sequence pomaga odrzucić starą próbkę, a dwa czasy wykryć buforowanie offline. Jedna aktualna pozycja nie dowodzi, że całe urządzenie pozostanie dostępne.

Nie zapisuję i nie wysyłam wszystkich próbek z jednakową częstotliwością. Dla podglądu klienta często wystarczy latest-wins i ograniczony update rate; dla audytu wybrane próbki trafiają do historii. Routing subskrypcji wymaga uprawnionego orderId i terminowego usunięcia dostępu po dostawie. Przy reconnect wysyłam najnowszy snapshot, potem aktualizacje.

## SD-18. Jak zaprojektować rezerwacje biletów lub noclegów bez double-bookingu?

Search pokazuje projekcję dostępności, ale autorytatywna rezerwacja atomowo zajmuje miejsce lub zakres dat w DB. Hold ma stan, termin i idempotency key; finalizacja płatności sprawdza, czy hold nadal obowiązuje. Unique/exclusion constraint lub model segmentów chroni niezmiennik.

Przy popularnym wydarzeniu stosuję admission/waiting room i shardowanie po zasobie. Dla noclegu ważne są granice przedziałów oraz strefa dat, dla numerowanego miejsca pojedynczy slot. Wygaśnięcie i spóźnione potwierdzenie płatności rozstrzyga jawna maszyna stanów z możliwością zwrotu.

Dla numerowanych miejsc unikalność może chronić aktywny przydział; dla noclegu trzeba wykrywać nakładające się zakresy. PostgreSQL może wspierać taki kontrakt exclusion constraint nad zakresem, ale szczegóły modelu i indeksu muszą być świadomie dobrane.

Wysoka konkurencja wymaga szybkiej odmowy i krótkiej transakcji zamiast tysięcy długich oczekiwań na jeden wiersz. Wygasły hold nie znika wyłącznie od działania schedulera: finalizacja sama sprawdza ważność atomowo. Dla opóźnionej płatności określam zwrot albo alternatywę, nie tworzę drugiej rezerwacji przez pominięcie sprawdzenia. Search pokazuje kandydatów, a zapis ostatecznie rozstrzyga dostępność.

## SD-19. Jak zaprojektować scentralizowane logowanie o skali petabajtów?

Agenty buforują ograniczony strumień, broker rozdziela ingestion, a pipeline waliduje, redaguje i kieruje dane do storage oraz indeksu. Gorący okres ma szybkie wyszukiwanie, dłuższa historia tańszy storage z wolniejszym odczytem. Partycjonuję po czasie/tenantach i kontroluję rozmiary rekordów.

Przy przeciążeniu wybieram sampling/drop dla mniej ważnych logów; audyt z gwarancją trwałości ma osobny kontrakt. Pipeline nie może blokować wszystkich requestów. Niska latency dowolnego zapytania po całych petabajtach wymaga dodatkowego kosztu i ograniczeń, nie jest darmową cechą indeksu.

Wolumen liczę jako zdarzenia/s × średni rozmiar × retencja × replikacja/indeksowanie. Petabajtowy pipeline potrzebuje kontroli kosztu, podziału tenantów i planu odbudowy, nie tylko „Elasticsearch cluster”. Logi aplikacyjne i nieusuwalny audit mogą mieć różne storage oraz gwarancje.

Gdy storage zwalnia, agenty mają skończony bufor i określoną politykę. Można zrzucić debug logi, ale nie wolno niejawnie utracić wymaganego dowodu rozliczenia. Query service ogranicza zakres czasu i koszt zapytań, aby jeden regex po całej historii nie zablokował ingestion ani pozostałych odbiorców.

## SD-20. Jak zaprojektować monitoring i alerting z kontrolą kardynalności?

Kolektory zbierają metryki do partycjonowanego TSDB, reguły obliczają SLI i stany alertów, a dispatcher grupuje/deduplikuje powiadomienia. Etykiety mają ograniczone wartości; exemplars/trace służą do przejścia do szczegółów bez etykiet per request.

Reguły uwzględniają brak danych, opóźnienie ingestion i czas utrzymywania warunku. Alert ma właściciela, runbook i kryterium zamknięcia. Awaria monitoringu wymaga zewnętrznego sygnału/heartbeat; sam system nie wykryje własnego całkowitego odcięcia kanału powiadomień.

TSDB partitionuje szeregi i czas, a reguły obliczają okna z uwzględnieniem opóźnionych próbek. Alert nie powinien oscylować od jednego krótkiego braku danych, ale też nie milczeć po całkowitym odcięciu telemetry. Osobny watchdog ocenia „monitoring monitoringu”.

Deduplikacja powiadomień ma klucz oparty na tożsamości problemu, nie pojedynczym timestampie. Grupowanie wielu błędów zależności pomaga uniknąć setek SMS z jednego incidentu. Mierzę także dostarczenie alertu i czas reakcji. Zbyt drogie label values i zbyt częste scraping mogą przeciążyć same monitorowane aplikacje.

## SD-21. Jak zaprojektować edytor współdzielony typu Google Docs?

Klient wysyła operacje z identyfikatorem i kontekstem wersji. Serwer/klienci uzgadniają wynik przez poprawnie zdefiniowany OT albo CRDT; operacje i snapshoty są trwałe, a reconnect odtwarza brakujące zmiany. Presence jest ulotna i nie musi mieć gwarancji dokumentu.

OT i CRDT mają różne założenia i koszty; nie wystarczy sortować zmian według zegara. Testuję zbieżność po duplikatach, opóźnieniach i pracy offline. Osobno rozwiązuję autoryzację, usunięcia i kompakcję historii, żeby stan nie rósł bez końca.

OT transformuje operacje względem współbieżnych zmian, a CRDT projektuje reprezentację/operacje umożliwiające zbieżność przy określonych założeniach. W obu przypadkach potrzebne są stabilne identyfikatory, deduplikacja i reguły usuwania; samo dopisanie Lamport timestamp nie tworzy edytora.

Trwały log i snapshot umożliwiają wznowienie, a compaction musi zachować informacje potrzebne urządzeniom długo offline. Testy generują różne kolejności dostawy i potwierdzają zbieżny wynik oraz dozwolone ACL. Zmiany uprawnień podczas offline wymagają osobnej polityki akceptowania później nadesłanych operacji. Presence można zgubić; treści dokumentu niekoniecznie.

## SD-22. Jak zaprojektować rozproszony scheduler z retry i „exactly-once execution”?

Trwały job zawiera next run, priorytet, stan i klucz logicznego wykonania. Scheduler/worker atomowo claimuje zadania przez DB lub kolejkę z lease; heartbeat i fencing ograniczają skutki utraty właściciela. Retry zachowuje tożsamość wykonania.

Crash po efekcie, przed ack, oznacza możliwe ponowne wykonanie handlera. Gwarantuję pojedynczy efekt przez idempotencję/unikalność w docelowym zasobie, zamiast obiecywać dokładnie jeden start procesu. Definiuję strefy, missed runs, overlapping i dead jobs oraz operacyjne wznowienie.

Claim zadania można oprzeć na krótkiej transakcji DB, która wybiera gotowe rekordy i nadaje lease/epoch. Worker wykonuje pracę poza długą blokadą, odnawia lease i zapisuje wynik zgodnie z wersją ownershipu.

<!-- example:SD-22-1 kind:text-model -->
**Kontekst:** Model lub przebieg operacji zapisany tekstowo; nie jest programem do uruchomienia.

~~~text
jobId + scheduledOccurrence → executionKey
executionKey → wiele prób, jeden dozwolony efekt
~~~

To kontrakt logiczny, nie kompletna implementacja. Przerwa między efektem a ack nadal pozwala uruchomić próbę drugi raz. Scheduler potrzebuje dead-letter/parked state, ręcznego wznowienia, limitu retry i decyzji missed runs. Cron według lokalnej godziny wymaga także reguł DST.

<!-- follow-up:SD-22 -->
**Sprawdź rozumienie:** Worker wykonał skutek i padł przed ack. Którą gwarancję można realistycznie utrzymać?

<details>
<summary>Kierunek odpowiedzi</summary>

Ponowne uruchomienie może być konieczne. Pojedynczy efekt zapewnia idempotencja/unikalność w zasobie docelowym i recovery, nie obietnica dokładnie jednego startu handlera.

</details>

## SD-23. Jak zaprojektować workflow engine wykonujący DAG?

Definicja DAG jest wersjonowana i walidowana na cykle. Instancja utrzymuje trwałe stany węzłów, zależności, próby i wyniki. Gotowe węzły trafiają do kolejki, a ukończenie atomowo odblokowuje następców lub zapisuje zdarzenie do dokończenia.

Worker może paść po skutku, więc task ma idempotency key i lease. Retry, timeout, anulowanie i kompensacje są częścią modelu, nie wyjątkami w pamięci. Nie zmieniam definicji aktywnej instancji bez reguł migracji; duże payloady trzymam poza metadanymi engine.

Gotowość węzła DAG jest konsekwencją trwałych wyników poprzedników. Jeśli dwa poprzedniki kończą się jednocześnie, oba mogą próbować zaplanować tego samego następcę; unikalny klucz uruchomienia lub atomowe przejście stanu rozstrzyga duplikat.

Nie przechowuję całego wielogigabajtowego wyniku w jednym rekordzie workflow: metadane wskazują wersjonowany artefakt. Anulowanie zatrzymuje przyszłe kroki, ale już wykonany zewnętrzny efekt może wymagać kompensacji. Zmiana definicji DAG podczas działania instancji ma własny plan migracji. Monitoring pokazuje blocked nodes, attempts i najstarsze aktywne zadania.

## SD-24. Jak zaprojektować reklamy i bidding poniżej 100 ms z kontrolą budżetu?

Rozdzielam szybkie pobranie kandydatów i aukcję od asynchronicznych rozliczeń/raportowania. Kampanie i modele mają lokalne, wersjonowane snapshoty; każda zewnętrzna oferta ma krótki deadline. Pacing przydziela budżety na czas i węzły, żeby nie wydać całego limitu na początku dnia.

Twardy globalny budżet wymaga rezerwacji lub koordynacji; luźny dopuszcza jawnie ograniczony overspend. Impression/click deduplikuję i wykrywam nadużycia. Niepewnego rozliczenia nie rozwiązuję samą pamięciową sumą liczników. Mierzę zarówno latency aukcji, jak i poprawność kosztów.

Lokalna decyzja aukcyjna potrzebuje aktualnej oferty, dostępnego budżetu i krótkiego deadline; raportowanie można opóźnić. Rezerwacja części budżetu na węzeł ogranicza globalną koordynację, ale pozostawia środki chwilowo niedostępne gdzie indziej. Bez rezerwacji trzeba zaakceptować ograniczony overspend.

Impression i billing nie muszą oznaczać tego samego momentu. Zdarzenia są deduplikowane, a korekty fraud mają osobny ledger. Ustalam, czy spóźniona odpowiedź licytującego jest odrzucana i jak rozliczyć timeout. Twarde sub-100 ms wymaga lokalnych danych oraz realistycznych granic sieciowych.

## SD-25. Jak zaprojektować IoT ingestion dla milionów niestabilnych połączeń?

Gatewayy uwierzytelniają urządzenia i obsługują lekki protokół, np. MQTT, z limitem wiadomości i payloadów. Broker/log przejmuje trwałość, a przetwarzanie normalizuje dane do magazynu szeregów czasowych i reguł alarmowych. Dzielę po device ID, żeby kontrolować porządek urządzenia.

Sekwencja i deduplikacja obsługują ponowne dostawy, a event time odróżnia zdarzenie od czasu przyjęcia. Reconnect ma jitter, kolejki offline mają limit. Awaria części gatewayów nie powinna od razu wywołać fali jednoczesnych reconnectów na pozostałe.

Urządzenie może potwierdzić transportowo wiadomość, ale nie oznacza to ukończonej analizy biznesowej. Dlatego określam moment trwałego przyjęcia w brokerze/logu i osobny status przetwarzania. QoS transportu nie zastępuje deduplikacji zdarzeń po restarcie.

Miliony połączeń wymagają limitów per device/tenant, heartbeat i stopniowego reconnect. Wrażliwe polecenia do urządzenia mają tożsamość, ważność i potwierdzenie wykonania, żeby stara komenda po długim offline nie uruchomiła niebezpiecznego działania. Historii nie sortuję ślepo według czasu klienta; analizuję jakość zegara i spóźnione próbki.

## SD-26. Jak zaprojektować backend gry multiplayer z synchronizacją i lag compensation?

Autorytatywny serwer meczu przetwarza wejścia w tickach, wysyła snapshoty/delty, a klient stosuje prediction i interpolation. Numerowane wejścia i snapshoty pozwalają na reconciliation. Region dobieram do latency graczy, a mecze partycjonuję jako jednostki stanu.

Lag compensation odtwarza ograniczoną historię zgodnie z regułami gry, nie ufa dowolnemu czasowi klienta. Failover meczu wymaga checkpointu i kosztuje utratę czasu/stanu; nie zawsze jest opłacalny. Oddzielam gameplay od zakupów i trwałego postępu, które wymagają innych gwarancji.

Serwer przyjmuje wejście gracza, a nie arbitralną nową pozycję jako prawdę. Numer sekwencji pozwala rozpoznać duplikat i potwierdzić ostatnio zastosowane wejście. Klient koryguje przewidywanie na podstawie autorytatywnego snapshotu, z interpolacją dla innych graczy.

Tick budget ma sztywną granicę CPU; zbyt ciężka logika jednego meczu nie skaluje się przez dodanie API podów. Partycjonuję po meczu i kontroluję liczbę graczy. Checkpoint/failover ma koszt opóźnienia, więc dla części gier uczciwy restart meczu może być lepszy niż skomplikowana migracja. Zakupy wymagają trwałej, idempotentnej ścieżki niezależnej od ticków.

## SD-27. Jak zaprojektować rekomendacje z sygnałami realtime i modelami batch?

Batch uczy modele i tworzy kandydatów, a stream aktualizuje świeże cechy. Odczyt pobiera kandydatów, sprawdza dostępność/uprawnienia i rankuje w budżecie czasu. Feature definitions i wersje modeli są spójne między treningiem i servingiem.

Monitoruję drift, freshness, wyniki eksperymentów i koszt, nie tylko accuracy offline. Przy awarii modelu wracam do popularnych lub ostatnich treści. Cold start wymaga sygnałów ogólnych. Unikam wycieku przyszłych danych do treningu i pętli wzmacniających wyłącznie dotychczasowe ekspozycje.

Candidate generation ogranicza miliony treści do setek, a ranking wybiera kolejność w budżecie. Real-time feature, np. ostatni klik, ma czas ważności i wersję definicji, żeby training-serving skew nie zmienił znaczenia cech. Model jest niezmiennym artefaktem z metrykami i możliwością cofnięcia.

Eksperyment mierzy nie tylko klik, lecz także satysfakcję, długoterminowy wynik i bezpieczne ograniczenia. Fallback do popularnych treści musi nadal filtrować prywatność, kraj i usunięcia. Cache rekomendacji nie może przetrwać cofnięcia dostępu tylko dlatego, że wynik modelu był kiedyś poprawny.

## SD-28. Jak zaprojektować CDN i wybór edge oraz invalidację?

DNS/anycast i routing operatora kierują klienta do odpowiedniego edge na podstawie topologii, dostępności i polityki, niekoniecznie fizycznie najbliższego. Edge sprawdza klucz cache, freshness i uprawnienia, a miss kieruje przez shield lub bezpośrednio do origin.

Dla statycznych treści preferuję wersjonowane URL; purge musi rozchodzić się globalnie i może trwać. Signed URL, cookies i Vary wpływają na cache key. Przy origin down dozwolone stale dane ograniczają skutki, ale prywatne/odwołane treści wymagają ostrzejszego kontraktu.

Cache key może obejmować host, path, wybrane query i nagłówki. Pominięcie parametru wpływającego na treść daje błędne odpowiedzi, a uwzględnienie każdego losowego parametru niszczy hit ratio. Prywatna reprezentacja nie może trafić do wspólnego cache bez właściwego kontraktu.

Wersjonowany URL /app.hash.js pozwala długo cachować niezmienną treść. Dla odwołanego materiału potrzebny jest purge/token policy o znanym opóźnieniu. Origin shield ogranicza wiele równoczesnych miss z edge. DNS/anycast kieruje według sieci i zdrowia, a nie prostego pomiaru kilometrów do użytkownika.

## SD-29. Jak zaprojektować web crawler?

URL frontier deduplikuje znormalizowane adresy i planuje pobrania z limitem per host. Fetchery respektują robots/politeness i limity odpowiedzi, zapisują treść oraz nowe linki, a pipeline indeksuje dokumenty. Priorytet uwzględnia świeżość i budżet crawlowania.

Chronię przed SSRF, redirectami do prywatnych sieci, pułapkami nieskończonych URL i decompression bombs. Retry ma backoff; checkpoint umożliwia restart. Deduplikacja URL i treści to różne problemy. Zbyt agresywna normalizacja może połączyć różne zasoby.

Frontier potrzebuje deduplikacji i priorytetów per host, aby jedna domena nie zajęła wszystkich fetcherów. Robots i limit pobrań są częścią polityki, ale nie ochroną przed technicznym SSRF. Po redirect oraz po rozwiązaniu DNS ponownie oceniam dozwolone adresy według bezpiecznego klienta.

Zapis crawl result jest idempotentny po URL/version, a indeksowanie może działać później. Nieudane strony dostają ograniczone retry i kolejny termin odświeżenia. Parametry sesji lub kalendarze mogą tworzyć nieskończony graf URL, więc potrzebne są budżety i heurystyki, których błędy mierzę przez coverage i duplikację treści.

## SD-30. Jak zaprojektować trwały rozproszony key-value store?

API put/get/delete określa wersje i wymagany model spójności. Routing wybiera shard, lider/uzgodniony protokół zapisuje WAL i repliki, a snapshot oraz kompakcja ograniczają koszt historii. Ownership i rebalancing są wersjonowane.

Usunięcia potrzebują tombstones, żeby stara replika nie wskrzesiła danych. Quorum, wybór lidera, naprawa i fencing stanowią protokół poprawności, nie listę luźnych technologii. Definiuję zachowanie przy partition i crashu po zapisie przed odpowiedzią; Redis cache i trwały system record to różne kontrakty.

W magazynie LSM write trafia do WAL i struktury pamięciowej, potem do plików oraz compaction; silnik oparty na B-tree ma inne koszty aktualizacji i odczytu. Wybór wpływa na read/write amplification, a nie tylko nazwę bazy.

Protokół replikacji określa moment potwierdzenia i możliwość odczytu po failover. Tombstones muszą przetrwać wystarczająco długo, by stare repliki nie wskrzesiły usuniętego klucza. Naprawa i rebalancing konkurują o I/O z ruchem klienta, więc mają limity. API wersjonowane put-if-version pozwala chronić przed lost update w określonej granicy.

## SD-31. Jak zaprojektować rozproszoną kolejkę zadań typu Kafka?

Partycjonowany trwały log przechowuje rekordy, replikacja zabezpiecza awarię węzła, a grupy workerów i offsety opisują postęp. Producent wybiera klucz, batching i potwierdzenia. Retencja pozwala replay, ale wymaga capacity planning i kontroli wolnych grup.

Określam ack, redelivery, ordering oraz odzyskanie po utracie workera. Kolejka z widocznością per zadanie i log z kursorem grupy mają odmienne kompromisy. Długie zadanie nie może blokować nieograniczonej liczby innych; potrzebne są limity in-flight, retry i obsługa poison message.

Log jest sekwencją z retencją, a work queue często ma stan i lease poszczególnych zadań. Długotrwałe zadanie w partycji może opóźnić cały prefiks commitów, nawet jeśli późniejsze prace już skończono. Odmiana kolejki musi odpowiadać potrzebie ordering i niezależności zadań.

Broker potrzebuje kontroli storage, replikacji, backlogu i quota tenantów. Producent po timeoutcie ma wynik nieznany; konsument po crashu możliwe redelivery. Identyfikator zadania i idempotentny handler pozostają potrzebne również w projekcie „Kafka-like”. Test awarii powinien obejmować utratę lidera, wolnego konsumenta i odtworzenie offsetu.

## SD-32. Jak zaprojektować rozproszony generator ID?

Opcje to centralna sekwencja z przydziałem zakresów, UUID lub układ typu czas + identyfikator węzła + licznik. Wybieram według wymagań: unikalność, rozmiar, porządek, przepustowość i praca przy braku koordynatora. Publiczny ID nie powinien ujawniać wrażliwych informacji.

W wariancie czasowym obsługuję cofnięcie zegara, restart i kolizję node ID; samo „Snowflake” nie jest dowodem unikalności. Zakresy ograniczają kontakt z koordynatorem kosztem luk. Ścisły globalny porządek i dostępna generacja offline wymagają sprzecznych założeń, które trzeba rozstrzygnąć.

Dla układu czas-node-counter liczba bitów określa horyzont czasu, liczbę węzłów i maksymalną liczbę ID na jednostkę czasu. Gdy licznik się wyczerpie, generator musi poczekać lub odmówić; nie może zawinąć do już użytej wartości. Node ID wymaga unikalnego przydziału i odzyskania po restarcie.

UUID rozwiązuje inne kompromisy: probabilistyczną unikalność i mniejszą potrzebę koordynacji, lecz porządek/rozmiar indeksu zależą od wariantu. Sekwencja centralna z zakresami dopuszcza luki. Żadne z tych ID nie powinno samo służyć jako sekret uprawniający do odczytu zasobu.

<!-- labs:start -->
## Powiązane laboratoria

Poniższe materiały rozwijają wybrane mechanizmy rozdziału. Każde laboratorium ma własne założenia, ograniczenia i komendy weryfikacji.

- [URL Shortener — wykonywalne studium System Design](../system-design/url-shortener/README.md)
- [Chat System — System Design](../system-design/chat-system/README.md)
- [System Design — E-commerce Platform](../system-design/ecommerce/README.md)
- [Distributed Rate Limiter](../system-design/rate-limiter/README.md)
<!-- labs:end -->
