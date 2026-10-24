# Płatności i bankowość

[Spis rozdziałów](README.md) · [Książki i źródła](MATERIALY.md#14-płatności-i-bankowość)

To odpowiedzi o architekturze i poprawności danych. Wymagania regulacyjne, retencja, lokalizacja danych i reguły ryzyka są wejściem do projektu, ustalanym dla konkretnego rynku i organizacji; sam wybór komponentów nie dowodzi zgodności.

<!-- question-toc:start -->
## Spis pytań

- [PAY-01. Jak zaprojektować skalowalny system płatności bez podwójnych obciążeń?](#pay-01-jak-zaprojektować-skalowalny-system-płatności-bez-podwójnych-obciążeń)
- [PAY-02. Jak zaprojektować ledger z podwójnym zapisem dla milionów kont?](#pay-02-jak-zaprojektować-ledger-z-podwójnym-zapisem-dla-milionów-kont)
- [PAY-03. Jak zaprojektować Loan Management System?](#pay-03-jak-zaprojektować-loan-management-system)
- [PAY-04. Jak zbudować real-time fraud detection bez zauważalnego opóźnienia checkout?](#pay-04-jak-zbudować-real-time-fraud-detection-bez-zauważalnego-opóźnienia-checkout)
- [PAY-05. Jak obsłużyć płatność międzynarodową, FX, banków korespondentów i SWIFT?](#pay-05-jak-obsłużyć-płatność-międzynarodową-fx-banków-korespondentów-i-swift)
- [PAY-06. Jak zaprojektować audytowalne raportowanie transakcji po latach?](#pay-06-jak-zaprojektować-audytowalne-raportowanie-transakcji-po-latach)
- [PAY-07. Jak zbudować niezmienny audit trail zmian konta?](#pay-07-jak-zbudować-niezmienny-audit-trail-zmian-konta)
- [PAY-08. Jak utrzymać spójność salda aplikacji mobilnej z core banking?](#pay-08-jak-utrzymać-spójność-salda-aplikacji-mobilnej-z-core-banking)
- [PAY-09. Jak zrobić multi-region failover przy ograniczeniach lokalizacji danych?](#pay-09-jak-zrobić-multi-region-failover-przy-ograniczeniach-lokalizacji-danych)
- [PAY-10. Jak uzgadniać ledger z clearing house na koniec dnia?](#pay-10-jak-uzgadniać-ledger-z-clearing-house-na-koniec-dnia)
- [PAY-11. Jak zaprojektować globalną autoryzację karty z celem poniżej 200 ms?](#pay-11-jak-zaprojektować-globalną-autoryzację-karty-z-celem-poniżej-200-ms)
- [PAY-12. Jak przetworzyć plik milionów przelewów z pojedynczym efektem biznesowym?](#pay-12-jak-przetworzyć-plik-milionów-przelewów-z-pojedynczym-efektem-biznesowym)
- [PAY-13. Jak skalować KYC/AML bez zablokowania onboardingu?](#pay-13-jak-skalować-kycaml-bez-zablokowania-onboardingu)
- [PAY-14. Jak zbudować sagę debit → FX → credit?](#pay-14-jak-zbudować-sagę-debit--fx--credit)
- [PAY-15. Jak tokenizować numery kart, żeby downstream nie widział PAN?](#pay-15-jak-tokenizować-numery-kart-żeby-downstream-nie-widział-pan)
- [PAY-16. Jak wykrywać account takeover bez blokowania poprawnych logowań?](#pay-16-jak-wykrywać-account-takeover-bez-blokowania-poprawnych-logowań)
<!-- question-toc:end -->

## PAY-01. Jak zaprojektować skalowalny system płatności bez podwójnych obciążeń?

Payment ma trwałe ID, idempotency key, kwotę/walutę i maszynę stanów, np. CREATED → AUTHORIZED → CAPTURED albo FAILED/UNKNOWN. Adapter operatora dostaje stabilną tożsamość operacji. Lokalny zapis stanu i eventu jest atomowy przez outbox.

Webhooki są uwierzytelniane i deduplikowane, mogą przyjść przed odpowiedzią HTTP lub wielokrotnie. Timeout nie oznacza odmowy: sprawdzam status i rekoncyliuję. Pojedynczy efekt wymaga współpracy granicy zewnętrznej; globalne „exactly-once” bez założeń jest nieuczciwą obietnicą. Refund jest nową operacją z własnym ID.

Autoryzacja blokuje/akceptuje możliwość pobrania, capture realizuje pobranie zgodnie z modelem operatora, a settlement i reconciliation rozliczają późniejszy przepływ. Łączenie ich w pojedynczy status PAID utrudnia opis timeoutów i refundów. Maszyna stanów musi odrzucać niedozwolone przejścia i uwzględniać webhook przed odpowiedzią HTTP.

<!-- example:PAY-01-1 kind:text-model -->
**Kontekst:** Model lub przebieg operacji zapisany tekstowo; nie jest programem do uruchomienia.

~~~text
paymentId → operationId → providerReference
jedna operacja → wiele prób transportowych → jeden rozstrzygnięty wynik
~~~

Nie ponawiam UNKNOWN jako nowego paymentId. Proces sprawdza status operatora i utrzymuje audit decyzji. Metryki obejmują stare UNKNOWN/PENDING, nie tylko błędy endpointu.

## PAY-02. Jak zaprojektować ledger z podwójnym zapisem dla milionów kont?

Journal transaction zawiera niezmienne pozycje debit/credit z ustalonym planem kont; księgowanie bilansuje się w zdefiniowanej jednostce/walucie. Unique business transaction ID blokuje powtórne zaksięgowanie. Zapis wszystkich pozycji i kontrola reguł są jedną transakcją w ustalonej granicy.

Saldo może być projekcją, lecz dostępne środki uwzględniają również rezerwacje. Korekta dodaje wpis odwracający, zamiast edytować historię. Sharding po koncie komplikuje operacje między shardami: potrzebny jest protokół transferu i uzgodnienia, nie dwa niezależne zapisy liczb.

Przykład transferu 100 jednostek z A do B obejmuje powiązane pozycje księgowe, a nie dwie niezależne aktualizacje liczb. Suma zgodnie z konwencją debit/credit musi się bilansować, a rezerwacja dostępnych środków jest osobną regułą. Sam bilans równy zero nie zapobiega wydaniu tego samego salda dwa razy.

<!-- example:PAY-02-1 kind:sql-template -->
**Kontekst:** Szablon SQL — wymaga bazy, schematu i podstawienia parametrów; skrypt go nie wykonuje.

~~~sql
CREATE UNIQUE INDEX uq_journal_operation
ON journal_transaction (business_operation_id);
~~~

To jeden constraint szkicu, nie pełny ledger. Dla różnych walut potrzebne są konta rozliczeniowe i jawne przeliczenia, a nie dodanie 100 EUR do 100 USD. Korekta przechowuje powód i referencję do oryginału.

<!-- follow-up:PAY-02 -->
**Sprawdź rozumienie:** Dwie kopie tego samego przelewu dają bilans debit/credit równy zero. Czy ledger jest przez to poprawny?

<details>
<summary>Kierunek odpowiedzi</summary>

Nie. Bilansowanie nie zapobiega podwójnemu policzeniu. Potrzebny jest unikalny business operation ID, atomowy journal i ochrona dostępnych środków. Różne niezmienniki wymagają osobnych zabezpieczeń.

</details>

## PAY-03. Jak zaprojektować Loan Management System?

Rozdzielam wniosek, ocenę, umowę, wypłatę, harmonogram, raty i księgowanie. Warunki umowy oraz kalkulacja odsetek/opłat są wersjonowane i deterministyczne z jawną walutą, zaokrągleniem i kalendarzem. Zdarzenie wpłaty ma unikalną tożsamość i regułę alokacji.

Zmiana stopy lub restrukturyzacja tworzy nową wersję, nie nadpisuje podstawy dawnych rozliczeń. Integracje wypłaty/ściągnięcia środków są idempotentne, a opóźnione komunikaty trafiają do rekoncyliacji. Odczyty portfela można skalować projekcjami bez osłabiania poprawności pojedynczego kontraktu.

Harmonogram rat jest wersjonowanym wynikiem warunków umowy i kalendarza. Wcześniejsza spłata, zmiana oprocentowania albo wakacje kredytowe nie powinny nadpisywać bez śladu danych, z których powstały historyczne naliczenia. Utrwalam reguły i zaokrąglenia użyte dla konkretnego okresu.

Wpłata ma stabilne ID, walutę i datę wartości; reguła alokacji określa, co pokrywa w pierwszej kolejności. Retry importu nie może ponownie obniżyć zadłużenia. Dla milionów umów batch dzielę po stabilnym kluczu z checkpointem, a wynik naliczenia można ponownie obliczyć i uzgodnić z ledgerem.

## PAY-04. Jak zbudować real-time fraud detection bez zauważalnego opóźnienia checkout?

Ścieżka synchroniczna pobiera dostępne cechy i wykonuje reguły/model w małym budżecie; stream aktualizuje velocity i sygnały, a cięższa analiza działa asynchronicznie. Wynik obejmuje score, decyzję i wersję reguł/modelu. Krytyczne cechy mają określoną świeżość.

Przy niedostępności modelu polityka domeny wybiera fallback: ograniczenie, step-up lub odmowę dla danej klasy ryzyka. Nie ma jednego uniwersalnego fail-open. Mierzę false positives, straty, czas i drift, a replay nie może wielokrotnie naliczyć tej samej transakcji do cech.

Ścieżkę realtime rozdzielam na pobranie cech, reguły/model i decyzję w deadline. Cecha velocity powinna liczyć unikalne transakcje, nie próby dostarczenia eventu, inaczej retry sztucznie podnosi ryzyko. Asynchroniczne wzbogacanie może wykryć późniejszy problem, ale nie cofnie niezależnie zakończonego checkoutu.

Wersja modelu, cech i reason codes umożliwia analizę, dlaczego decyzja zapadła. False positives mierzę razem ze stratami i odsetkiem step-up. Fallback zależy od ryzyka: dla małej znanej transakcji może być inny niż dla pierwszej dużej wypłaty. Model nie powinien sam nadawać wyjątków od polityki.

## PAY-05. Jak obsłużyć płatność międzynarodową, FX, banków korespondentów i SWIFT?

Trwały proces przechowuje zlecenie, identyfikatory komunikatów, opłaty, kurs/ważność oferty i stany rozrachunku. Adaptery tłumaczą protokoły oraz potwierdzenia. Wysłanie komunikatu SWIFT nie jest równoznaczne z ostatecznym rozliczeniem pieniędzy.

Każdy krok ma idempotencję, audyt, timeout i procedurę obsługi wyniku nieznanego. Ledger rozdziela rezerwację, księgowanie i clearing. Cut-off, dni robocze i zmiana kursu są jawnymi regułami. Po nieodwracalnym kroku naprawa może wymagać zwrotu/reversal i udziału operacji.

Każdy zewnętrzny uczestnik może mieć własne cut-off, potwierdzenia, korekty i finalność. Proces przechowuje zarówno stan technicznej dostawy komunikatu, jak i stan biznesowego przelewu. „ACK komunikatu” nie oznacza zaksięgowania przez bank odbiorcy.

Kurs FX ma identyfikator oferty, ważność oraz regułę zaokrągleń/opłat. Przy wygaśnięciu nie wolno cicho użyć nowej ceny wbrew zaakceptowanej ofercie. Rekoncyliacja łączy referencje operatorów z wewnętrznym ID. Gdy nie da się automatycznie rozstrzygnąć niepewności, sprawa trafia do operacji z kompletną historią prób i jasnym zakazem ponownego zlecenia.

## PAY-06. Jak zaprojektować audytowalne raportowanie transakcji po latach?

Źródłowe fakty mają trwałe ID, czas zdarzenia i przyjęcia, wersję schematu oraz pochodzenie. Pipeline buduje raport dla konkretnej wersji reguł i przechowuje manifest wejścia, sumy kontrolne oraz wynik wysłania. Późne dane generują kontrolowaną korektę.

Odtworzenie historycznego raportu nie może zależeć od dzisiejszych wartości słowników. Oddzielam retencję dowodów od indeksu do szybkich zapytań i rejestruję dostęp. Horyzont przechowywania oraz format są wymaganiami konkretnego procesu, a nie dowolną liczbą lat z rozmowy.

Reprodukowalny raport musi znać wersję danych i reguł obowiązujących dla okresu. Dzisiejsza zmiana klasyfikacji klienta nie powinna bez jawnej polityki przepisać historycznego raportu. Każdy wynik ma lineage: wejściowe rekordy, korekty, transformacje i dowód wysłania.

Proces zasilania kontroluje kompletność, duplikaty i opóźnione zdarzenia. Ponowne wygenerowanie pliku nie może automatycznie wysłać drugiego zgłoszenia jako nowego. Parametry retencji i obowiązki raportowania są ustalonym wymaganiem konkretnej organizacji; rozwiązanie techniczne zapewnia audytowalność ich wykonania, ale nie ustala samodzielnie zasad prawnych.

## PAY-07. Jak zbudować niezmienny audit trail zmian konta?

Przy zapisie domenowym rejestruję co, kto, kiedy, z jakiego powodu i w jakiej wersji zmienił, z powiązaniem do żądania. Audyt wymaga kompletności i kontroli dostępu niezależnej od zwykłych logów. Archiwum append-only/WORM z weryfikacją integralności utrudnia manipulację.

Łańcuch hashy wykrywa pewne zmiany, ale administrator mogący przepisać cały łańcuch nadal jest zagrożeniem bez zewnętrznego zakotwiczenia/uprawnień. Nie zapisuję haseł i tokenów jako „pełnego stanu”. Indeks można odbudować, oryginalny dowód musi przetrwać.

Append-only w API aplikacji nie oznacza niezmienności dla administratora storage. Potrzebne są oddzielne uprawnienia, kontrola zmian i przechowywanie dowodu w warstwie trudniejszej do przepisania. WORM, podpisy i hashe rozwiązują różne części problemu.

Zdarzenie audytowe zawiera bezpieczny identyfikator aktora, operację, obiekt, wersję, powód i correlation ID. Nie musi zawierać pełnego poprzedniego hasła ani tokena. Odczyt historyczny wymaga wersjonowanych schematów i kluczy, a dostęp sam jest audytowany. Test obejmuje brak możliwości modyfikacji zwykłą rolą aplikacji oraz odtworzenie indeksu z oryginalnego archiwum.

## PAY-08. Jak utrzymać spójność salda aplikacji mobilnej z core banking?

Core ledger pozostaje źródłem prawdy, mobilny widok jest wersjonowaną projekcją z czasem aktualizacji. Rozróżniam saldo księgowe, dostępne i operacje PENDING. Po własnej operacji klient może dostać jej status lub odczyt z odpowiedniego źródła, zamiast obietnicy natychmiastowej globalnej świeżości.

Stream/CDC aktualizuje projekcję, a reconciliation wykrywa luki i duplikaty. Przy opóźnieniu oznaczam świeżość; decyzji o wydaniu środków nie opieram wyłącznie na starym cache. Niedostępna projekcja nie musi zatrzymać poprawnego księgowania.

Użytkownik po przelewie może zobaczyć nowe saldo dostępne, stary zapis księgowy i pozycję oczekującą — pod warunkiem że UI jasno wyjaśnia te znaczenia. Ukrycie różnic pod jedną liczbą „saldo” prowadzi do pozornych błędów i niepoprawnych decyzji.

Projekcja przechowuje wersję/pozycję źródła i czas aktualizacji. Po własnej operacji można odczytać status z autorytatywnego procesu lub poczekać na minimalną wersję. Jeśli stream zgubi event, okresowe uzgodnienie wykrywa lukę. Nie blokuję księgowania tylko dlatego, że mobilna projekcja jest chwilowo niedostępna, jeśli kontrakt pozwala odroczyć widok.

## PAY-09. Jak zrobić multi-region failover przy ograniczeniach lokalizacji danych?

Klasyfikuję dane i dozwolone regiony dla primary, replik, backupów, logów i kluczy. Ruch konta trafia do właściwej jurysdykcji/home region, a plan DR używa wyłącznie dopuszczalnych lokalizacji. RPO/RTO i koszt globalnej koordynacji są jawne.

Failover wymaga potwierdzenia stanu repliki, fencing poprzedniego primary oraz spójnego przełączenia routingu i operatorów płatności. Jeśli żaden dostępny region nie spełnia wymagań, kontrolowana niedostępność może być poprawnym zachowaniem. Samo „active-active” nie rozwiązuje lokalizacji ani double spend.

Plan regionów obejmuje także backupy, diagnostykę i usługi obsługi klienta; nie wystarczy ograniczyć samej tabeli klientów. Dane routingu mogą być minimalne i odrębnie sklasyfikowane. Żądania kieruję do właściwego home region zgodnie z tym podziałem.

Po utracie regionu sprawdzam ostatnią bezpiecznie odtworzoną pozycję, uruchamiam dopuszczalną replikę i odcinam starego writera. Callback operatora również musi trafić do nowego właściciela procesu. Jeśli brak dopuszczalnej lokalizacji, system może świadomie odmówić operacji. RPO nie może być jedynie deklaracją marketingową — mierzę je na testowym failover i uzgodnieniu ledgera.

## PAY-10. Jak uzgadniać ledger z clearing house na koniec dnia?

Importuję wersjonowane pliki/zdarzenia do staging, kontroluję kompletność, checksum, liczbę i sumę rekordów. Dopasowuję po stabilnych referencjach, kwocie, walucie i datach, a różnice klasyfikuję: brak, duplikat, opóźnienie, opłata lub rozbieżność wartości.

Wyjątki trafiają do trwałej kolejki wyjaśnień z właścicielem. Naprawa tworzy audytowalne korekty, nie nadpisuje historii, żeby „sumy pasowały”. Ponowny import nie może drugi raz księgować; rozróżniam finalny stan dnia od późnego rozrachunku.

Dopasowanie nie powinno opierać się wyłącznie na kwocie i dniu: wiele przelewów może mieć identyczne wartości. Preferuję stabilne referencje wewnętrzne i zewnętrzne, z dodatkowymi kontrolami waluty oraz sum. Dla braku jednoznaczności rekord pozostaje wyjątkiem, zamiast być automatycznie „dopasowany”.

<!-- example:PAY-10-1 kind:text-model -->
**Kontekst:** Model lub przebieg operacji zapisany tekstowo; nie jest programem do uruchomienia.

~~~text
plik → walidacja manifestu → staging → matching
     → matched / missing / duplicate / amount mismatch
     → korekta lub sprawa do wyjaśnienia
~~~

Ponowny import tego samego pliku jest idempotentny. Cut-off i spóźnione rozliczenia mają jawną regułę, aby chwilowa różnica nie została błędnie zaksięgowana jako strata.

<!-- follow-up:PAY-10 -->
**Sprawdź rozumienie:** W końcowym raporcie brakuje przelewu, który dotarł po cut-off. Czy trzeba natychmiast dopisać korektę salda?

<details>
<summary>Kierunek odpowiedzi</summary>

Najpierw sklasyfikuj różnicę jako opóźnienie/finalność okresu i dopasuj stabilną referencję. Nie księguj dla samego wyrównania sum. Reguła późnych danych i audytowana korekta muszą być jawne.

</details>

## PAY-11. Jak zaprojektować globalną autoryzację karty z celem poniżej 200 ms?

Rozkładam budżet na transport, walidację, ryzyko i atomową rezerwację środków, blisko autorytatywnego konta. Krytyczne dane/klucze są gotowe lokalnie, a długie integracje nie leżą na ścieżce. Duplikat autoryzacji ma tę samą referencję i wynik.

Definiuję percentyl i geografię: nie da się zagwarantować 200 ms dla dowolnej globalnej ścieżki sieci. Stand-in processing/offline limity wymagają jawnie zaakceptowanego ryzyka i późniejszego uzgodnienia. Autoryzacja, capture i settlement pozostają odrębnymi etapami.

Najkrótsza ścieżka powinna zawierać tylko dane potrzebne do autoryzacji: ważność instrumentu, dostępne środki/limity i ograniczony scoring. Długie pobieranie danych z wielu regionów łamie budżet, więc cechy trzeba przygotować wcześniej lub zastosować jawnie zaakceptowany fallback.

Dla 200 ms podaję percentyl, miejsce pomiaru i geografię, bo same odległości sieciowe mogą pochłonąć większość czasu. Rezerwacja ma stabilną referencję i późniejsze expiry/reversal. Przy wyniku nieznanym terminal lub integracja ponawia tę samą operację, a nie tworzy kolejną blokadę. Settlement i powiadomienie klienta mogą działać poza ścieżką odpowiedzi.

## PAY-12. Jak przetworzyć plik milionów przelewów z pojedynczym efektem biznesowym?

Identyfikuję plik przez manifest/checksum i batch ID, a każdą pozycję przez trwały klucz, którego unikalność chroni DB. Import jest wznawialny porcjami z checkpointem i kontrolą sum. Zewnętrzny adapter używa tej samej tożsamości przy ponowieniu.

Crash po wysłaniu, przed zapisem wyniku, prowadzi do sprawdzenia statusu i reconciliation, nie drugiego przelewu. Nie obiecuję dokładnie jednego wykonania pętli. Globalna atomowość całego pliku to dodatkowe wymaganie; porcje dają niezależny postęp i wynik każdej pozycji.

Plik ma manifest z liczbą pozycji i kontrolnymi sumami, ale to pozycja jest jednostką idempotencji. Przy wznowieniu worker może ponownie zobaczyć ostatni batch; unikalny klucz przelewu rozstrzyga, czy trzeba wykonać nowy skutek czy tylko odczytać wynik.

Checkpoint i zewnętrzny status muszą być uzgadniane po crashu. Jeśli partner nie wspiera idempotencji/statusu, nie można uczciwie zagwarantować automatycznego exactly-once na tej granicy. Wynik batcha pokazuje accepted, completed, rejected i unresolved, zamiast jednej flagi sukcesu dla miliona różnych rezultatów. Przepustowość uwzględnia również ograniczenia partnera.

## PAY-13. Jak skalować KYC/AML bez zablokowania onboardingu?

Proces ma trwałe stany zbierania dokumentów, weryfikacji, screeningu i ewentualnego review. Ciężkie wywołania działają asynchronicznie z limitami dostawców, a aktualizacje list/reguł są wersjonowane i pozwalają ponownie ocenić właściwy zbiór klientów.

Deduplikuję zgłoszenia i callbacki, monitoruję wiek spraw oraz kolejkę ręcznego review. Niedostępny screening nie powinien być interpretowany jako pozytywny wynik. Zakres dopuszczalnych funkcji konta przed zakończeniem weryfikacji jest wymaganiem biznesowym/regulacyjnym, ustalonym poza samą infrastrukturą.

Automatyzacja zdejmuje powtarzalną pracę, ale kolejka manual review może stać się nowym bottleneckiem. Mierzę wiek spraw, odsetek nierozstrzygniętych i powód skierowania do człowieka. Nie skracam tego czasu przez traktowanie timeoutu screeningu jako zgody.

Dokumenty i wyniki dostawców są wersjonowane, a retry używa tego samego case ID. Zmiana list lub danych klienta może wymagać ponownej oceny, z kontrolą zakresu i tempa. Dostęp do wrażliwych materiałów jest minimalny i audytowany. Zakres aktywności konta przed zakończeniem procesu musi wynikać z uzgodnionej polityki.

## PAY-14. Jak zbudować sagę debit → FX → credit?

Najpierw rezerwuję środki i utrwalam parametry FX; każdy krok ma ID, trwały stan oraz wynik. Orkiestrator przechodzi przez dozwolone etapy z outbox i retry. Credit nie może nastąpić dwukrotnie po ponownym komunikacie.

Kompensacja zależy od finalności: przed settlement można zwolnić hold, po wymianie/księgowaniu potrzebny jest reversal lub nowa transakcja z rozliczeniem różnicy kursowej. Timeout credit to wynik nieznany wymagający sprawdzenia, nie automatyczny zwrot debit. Mierzę i wyjaśniam długo pozostające stany pośrednie.

W każdym etapie sagi odróżniam polecenie, próbę transportową i rozstrzygnięty efekt. Debit może oznaczać hold, a FX rezerwację kursu lub faktycznie dokonane przeliczenie — kompensacje będą różne. Nie projektuję odwrotności bez poznania finalności partnerów.

Jeśli credit trwa zbyt długo, orkiestrator najpierw sprawdza status tej samej operacji. Spóźniony success po uruchomieniu kompensacji przechodzi przez jawną maszynę stanów i uzgodnienie, nie nadpisuje statusu bezwarunkowo. Ledger zawiera powiązane wpisy i korekty. Awaria orkiestratora nie gubi procesu, bo etap, wersja i deadline są trwałe.

## PAY-15. Jak tokenizować numery kart, żeby downstream nie widział PAN?

Zaufany komponent/provider przyjmuje PAN i wydaje token, a systemy zamówień przechowują token oraz minimalne dozwolone metadane. Odwzorowanie i ewentualna detokenizacja znajdują się w silnie ograniczonym środowisku z osobną tożsamością, audytem i zarządzaniem kluczami.

Token nie jest prostym hashem PAN ani automatycznym dowodem wyłączenia z zakresu wymagań. Trzeba określić możliwość odwrócenia, zakres użycia i przepływ danych. Historyczne [wytyczne tokenizacji PCI](https://www.pcisecuritystandards.org/documents/Tokenization_Guidelines_Info_Supplement.pdf) wyjaśniają mechanizm; aktualne wymagania ocenia się osobno dla wdrożenia.

Token może być ograniczony do konkretnego merchanta lub kontekstu użycia; skradziony token nie powinien automatycznie działać jak dowolny numer karty. O tym decyduje kontrakt dostawcy i proces tokenizacji, nie sama zamiana tekstu na losowy identyfikator.

Backend downstream dostaje token i minimalne metadane prezentacyjne, np. końcówkę numeru zgodnie z polityką. Detokenizacja, jeśli potrzebna, ma osobną ścisłą autoryzację. Audyt i logi również nie mogą przypadkowo odtworzyć PAN przez debug payload. Awaria vault nie powinna skłaniać do buforowania surowych kart w zwykłej kolejce.

## PAY-16. Jak wykrywać account takeover bez blokowania poprawnych logowań?

Łączę sygnały sesji/urządzenia, anomalie lokalizacji, tempo prób i zmianę zachowania w ocenę ryzyka, z wersją reguł oraz decyzji. Step-up authentication dla podejrzanej operacji pozwala odróżnić ryzyko od automatycznej odmowy każdego nietypowego wejścia.

Mierzę false positives i skuteczność, ograniczam enumerację kont oraz brute force. IP i geolokalizacja są sygnałami, nie dowodem tożsamości. Przy awarii modelu stosuję uzgodnioną politykę ryzyka i możliwość odzyskania dostępu, z kontrolą prywatności i retencji sygnałów.

Przykład decyzji risk-based: nowe urządzenie i nietypowa duża wypłata uruchamiają step-up, a samo logowanie z nowego IP podczas podróży nie musi oznaczać przejęcia. Istotne są korelacja sygnałów, poprawne etykiety i pomiar fałszywych blokad.

Atakujący może próbować wywołać blokadę konta ofiary przez wiele błędnych logowań, dlatego limity i recovery nie powinny łatwo tworzyć narzędzia denial-of-service. Zmiana danych odzyskiwania, sesji i MFA ma podwyższoną ochronę. Model nie jest jedyną bramką: deterministyczne polityki i bezpieczne ścieżki odzyskania nadal obowiązują podczas awarii scoringu.

<!-- labs:start -->
## Powiązane laboratoria

Poniższe materiały rozwijają wybrane mechanizmy rozdziału. Każde laboratorium ma własne założenia, ograniczenia i komendy weryfikacji.

- [Payment System — System Design](../system-design/payment-system/README.md)
- [consumer](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/consumer/README.md)
- [cdc reconciliation](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/cdc_reconciliation/README.md)
<!-- labs:end -->
