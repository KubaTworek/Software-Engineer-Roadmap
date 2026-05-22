# Integracja i konsystencja – wspólna baza danych, sieć i eventual consistency

## Wprowadzenie

Integracja między modułami lub mikroserwisami jest jednym z najtrudniejszych obszarów projektowania systemów e-commerce. W prostych aplikacjach wiele problemów można rozwiązać przez jedną bazę danych i jedną transakcję. Gdy jednak system rośnie, pojawiają się osobne bounded contexts, niezależne zespoły, różne modele domenowe i potrzeba autonomicznego skalowania. Wtedy najprostsze rozwiązania zaczynają generować ukryte koszty.

Najbardziej kuszącym sposobem integracji jest współdzielenie bazy danych. Moduły lub usługi mogą wtedy czytać i zapisywać te same tabele, wykonywać joiny oraz korzystać z jednej transakcji bazodanowej. Na początku wydaje się to wygodne, ponieważ nie trzeba projektować zdarzeń, kolejek, retry ani mechanizmów eventual consistency. Problem polega na tym, że taka integracja bardzo szybko prowadzi do silnego sprzężenia. Baza danych staje się ukrytym kontraktem między wieloma częściami systemu.

Alternatywą jest integracja przez sieć i zdarzenia. Każdy kontekst posiada własny model danych, a komunikacja odbywa się przez jawne kontrakty: API, komendy lub zdarzenia integracyjne. Taki model lepiej wspiera autonomię zespołów, skalowanie i ewolucję domeny, ale wprowadza nową złożoność: eventual consistency, idempotentność, retry, obsługę duplikatów, sagi i wzorce takie jak transactional outbox.

Nie istnieje rozwiązanie darmowe. Wspólna baza upraszcza spójność, ale niszczy izolację. Event-driven architecture wzmacnia izolację, ale wymaga dojrzałych mechanizmów integracyjnych.

## Wspólna baza danych jako najprostsza integracja

Wspólna baza danych oznacza, że wiele modułów lub usług korzysta z tych samych tabel. Na przykład moduł Sales zapisuje zamówienia, Billing bezpośrednio aktualizuje status płatności w tabeli zamówień, a Fulfillment odczytuje z tej samej tabeli informacje potrzebne do wysyłki.

Na początku takie podejście wydaje się bardzo praktyczne. Wszystkie dane są w jednym miejscu. Można łatwo wykonać joiny. Można objąć kilka zmian jedną transakcją. Debugowanie bywa prostsze, ponieważ stan systemu znajduje się w jednej bazie. Dla małego monolitu takie podejście może być wystarczające.

Problem pojawia się wtedy, gdy baza zaczyna pełnić rolę wspólnego kontraktu między kontekstami. Każda zmiana schematu musi być konsultowana z wieloma zespołami. Kolumna dodana przez jeden moduł może być używana przez inny. Usunięcie pola staje się ryzykowne, ponieważ trudno ustalić wszystkich konsumentów. Z czasem baza danych przestaje być detalem implementacyjnym jednego kontekstu, a staje się centralnym punktem sprzężenia całego systemu.

Wspólna baza łamie podstawową zasadę bounded contexts. Jeżeli Billing bezpośrednio aktualizuje tabele Sales, to Billing zaczyna zależeć od wewnętrznego modelu Sales. Jeżeli Fulfillment bezpośrednio czyta tabele zamówień, to model wysyłki zależy od struktury modelu sprzedaży. W praktyce oznacza to, że konteksty nie są już autonomiczne.

## Ukryte koszty współdzielonej bazy

Największym kosztem wspólnej bazy danych jest coupling. Nie jest on widoczny w kodzie aplikacyjnym tak wyraźnie jak zależność między klasami, ale jest równie groźny. Schemat bazy staje się interfejsem publicznym. Problem polega na tym, że zwykle nie jest wersjonowany ani dokumentowany tak starannie jak API.

Drugim kosztem jest utrudniona skalowalność. Gdy wszystkie moduły korzystają z jednej bazy, baza staje się wąskim gardłem. Obciążenie odczytowe jednego kontekstu może wpływać na zapis innego. Długie zapytania raportowe mogą spowolnić transakcje biznesowe. Locki i transakcje zaczynają oddziaływać na obszary, które logicznie powinny być niezależne.

Trzecim kosztem jest utrata autonomii zespołów. Jeżeli kilka zespołów współdzieli schemat, to każda zmiana wymaga koordynacji. Deployment aplikacji i migracje bazy stają się coraz bardziej ryzykowne. Zamiast niezależnych modułów powstaje system, w którym wszyscy muszą uważać na wszystkich.

Czwartym kosztem jest trudność migracji do mikroserwisów. Jeżeli system ma jedną wspólną bazę, wydzielenie mikroserwisu nie polega tylko na przeniesieniu kodu. Trzeba również wydzielić dane, zmienić sposób komunikacji, przygotować eventy, zapewnić migrację historycznych rekordów i rozwiązać problem zapytań przekrojowych. Wspólna baza często okazuje się największą przeszkodą w realnej dekompozycji systemu.

## Baza danych jako własność bounded context

W podejściu zgodnym z DDD każdy bounded context powinien posiadać własny model danych. W mikroserwisach często oznacza to zasadę database per service. Każdy serwis ma własną bazę, własny schemat i pełną kontrolę nad swoimi danymi. Inne serwisy nie czytają ani nie aktualizują tych tabel bezpośrednio.

W modularnym monolicie można fizycznie korzystać z jednej bazy, ale warto zachować logiczną separację. Oznacza to osobne schematy, osobne repozytoria, brak joinów między kontekstami i zakaz bezpośredniego dostępu do tabel należących do innego modułu. W ten sposób modularny monolit może przygotować system do późniejszego wydzielenia mikroserwisów.

Najważniejsza zasada brzmi: dane mają właściciela. Jeżeli Sales jest właścicielem zamówienia sprzedażowego, to Billing nie powinien samodzielnie zmieniać jego tabel. Billing może opublikować zdarzenie `PaymentCompleted`, a Sales może na nie zareagować i zaktualizować własny stan. To wydaje się bardziej złożone, ale chroni granice domenowe.

## Integracja przez zdarzenia

W architekturze event-driven bounded contexts komunikują się przez zdarzenia. Zdarzenie opisuje fakt biznesowy, który już się wydarzył. `OrderPlaced` oznacza, że zamówienie zostało złożone. `PaymentCompleted` oznacza, że płatność została zakończona. `InventoryReserved` oznacza, że produkty zostały zarezerwowane.

Zdarzenia pozwalają rozdzielić nadawcę od odbiorcy. Sales nie musi wiedzieć, że Billing utworzy płatność, Fulfillment zaplanuje wysyłkę, a moduł powiadomień wyśle e-mail. Sales publikuje fakt biznesowy, a zainteresowane konteksty reagują samodzielnie.

Taki model wzmacnia autonomię. Każdy kontekst może mieć własną bazę, własny model i własny sposób przetwarzania. Integracja odbywa się przez publiczne kontrakty zdarzeń, a nie przez współdzielone tabele. Dzięki temu zmiana wewnętrznej struktury bazy jednego kontekstu nie musi wpływać na inne, o ile kontrakt zdarzenia pozostaje stabilny.

Zdarzenia nie powinny jednak przenosić całego modelu domenowego. Zdarzenie integracyjne powinno zawierać minimalny zestaw danych potrzebny odbiorcom. Zbyt duże payloady zwiększają coupling i utrudniają wersjonowanie. Zbyt małe payloady mogą zmuszać odbiorców do synchronicznego dopytywania nadawcy. Wymaga to świadomego projektowania kontraktów.

## Eventual consistency

Eventual consistency oznacza, że system nie musi być spójny natychmiast po wykonaniu operacji, ale powinien osiągnąć spójność po pewnym czasie. Jest to naturalna konsekwencja rozproszonej architektury, w której każdy kontekst posiada własną bazę danych.

W systemie e-commerce po złożeniu zamówienia Sales może natychmiast wiedzieć, że zamówienie istnieje, ale Billing może jeszcze nie zdążyć utworzyć płatności, a Fulfillment może jeszcze nie zaplanować wysyłki. Przez krótki czas różne części systemu widzą różny etap procesu. To nie musi być błąd. Jest to świadomie zaakceptowany model spójności.

Eventual consistency wymaga zmiany sposobu myślenia. Nie projektujemy systemu jako jednej wielkiej transakcji obejmującej wszystkie obszary. Projektujemy proces jako sekwencję lokalnych transakcji połączonych zdarzeniami. Każdy kontekst zapisuje własny stan lokalnie, publikuje zdarzenie, a inne konteksty reagują we własnym czasie.

To podejście poprawia skalowalność i odporność, ale utrudnia obsługę błędów. Trzeba zdecydować, co zrobić, jeśli płatność się nie powiedzie, magazyn nie ma towaru albo zewnętrzny przewoźnik jest niedostępny. W takich przypadkach potrzebne są sagi, retry i akcje kompensacyjne.

## Transactional Outbox

Transactional Outbox jest jednym z najważniejszych wzorców integracyjnych w architekturze event-driven. Rozwiązuje problem atomowości między zapisem danych a publikacją zdarzenia.

Bez outboxa możliwa jest niebezpieczna sytuacja: serwis zapisuje zmianę w bazie danych, ale pada przed wysłaniem zdarzenia do brokera. Wtedy stan lokalny został zmieniony, ale inne konteksty nigdy się o tym nie dowiedzą. Możliwa jest też sytuacja odwrotna: zdarzenie zostaje opublikowane, ale transakcja bazodanowa nie zostaje zatwierdzona. Wtedy inne konteksty reagują na fakt, który nie istnieje w trwałym stanie nadawcy.

Outbox rozwiązuje ten problem przez zapis zdarzenia do tabeli outbox w tej samej transakcji co zmiana biznesowa. Jeżeli transakcja się powiedzie, zapisany zostaje zarówno agregat, jak i wiadomość outbox. Jeżeli transakcja się nie powiedzie, nie zostanie zapisane ani jedno, ani drugie.

Następnie osobny proces, często nazywany outbox relay, odczytuje nieopublikowane wiadomości i wysyła je do brokera. Po udanym wysłaniu oznacza wiadomość jako opublikowaną. Dzięki temu zdarzenie nie ginie, nawet jeśli serwis padnie między commitem a publikacją.

Outbox nie gwarantuje dokładnie jednorazowego przetwarzania w całym systemie. W praktyce trzeba założyć, że wiadomość może zostać wysłana lub dostarczona więcej niż raz. Dlatego outbox powinien być używany razem z idempotentnymi konsumentami.

## Idempotent Consumer

W systemach rozproszonych należy zakładać at-least-once delivery. Oznacza to, że broker, outbox relay lub mechanizmy retry mogą dostarczyć tę samą wiadomość więcej niż raz. Nie jest to sytuacja wyjątkowa. To normalny element projektowania systemów odpornych na awarie.

Idempotentny konsument to taki, który może bezpiecznie przetworzyć ten sam komunikat wielokrotnie, a efekt biznesowy pozostanie taki sam jak po jednym przetworzeniu. Przykładowo Billing nie powinien utworzyć dwóch faktur, jeśli dwa razy otrzyma `OrderPlaced`. Fulfillment nie powinien utworzyć dwóch przesyłek dla tego samego zamówienia, jeśli dwukrotnie otrzyma ten sam event.

Typowym rozwiązaniem jest tabela przetworzonych wiadomości, na przykład `processed_messages`. Konsument przed wykonaniem operacji sprawdza, czy identyfikator wiadomości był już przetworzony. Jeżeli tak, ignoruje komunikat. Jeżeli nie, wykonuje operację i zapisuje identyfikator jako przetworzony. W praktyce warto używać unikalnego constraintu na parze `consumer_name` oraz `message_id`, aby baza danych chroniła przed race condition.

Idempotentność nie jest dodatkiem. Jest warunkiem bezpieczeństwa w architekturze event-driven. Bez niej retry i outbox mogą prowadzić do podwójnych płatności, podwójnych rezerwacji albo błędnych statusów.

## Retry i obsługa błędów

Retry jest konieczny, ponieważ sieć i zewnętrzne usługi zawodzą. Broker może chwilowo nie odpowiadać, operator płatności może mieć opóźnienie, baza może odrzucić połączenie, a inny serwis może być chwilowo niedostępny.

Retry powinien być jednak stosowany ostrożnie. Nie każdy błąd nadaje się do ponowienia. Błąd walidacji danych nie zniknie po kolejnej próbie. Natomiast timeout, chwilowy brak połączenia albo przeciążenie zależności może być błędem przejściowym. Dlatego system powinien rozróżniać błędy trwałe i przejściowe.

Retry powinien mieć limity. Nieskończone ponawianie może przeciążyć zależność, która już ma problem. Często stosuje się exponential backoff, jitter oraz dead letter queue dla wiadomości, których nie udało się przetworzyć po wielu próbach. W komunikacji synchronicznej przydatny jest circuit breaker, który przerywa wywołania do usługi uznanej za niedostępną i pozwala systemowi szybciej degradować funkcjonalność.

Retry zwiększa szansę powodzenia operacji, ale wymaga idempotentności. Jeżeli operacja nie jest idempotentna, ponowienie może być groźniejsze niż pierwotny błąd.

## Sagi

Saga jest wzorcem koordynacji długiego procesu biznesowego obejmującego wiele kontekstów. W systemie e-commerce takim procesem może być obsługa zamówienia: złożenie zamówienia, autoryzacja płatności, rezerwacja magazynu, przygotowanie wysyłki i ewentualna kompensacja w przypadku błędu.

W architekturze mikroserwisowej nie należy próbować obejmować całego tego procesu jedną transakcją rozproszoną. Zamiast tego każdy krok jest lokalną transakcją w swoim kontekście. Saga koordynuje te lokalne transakcje i określa, co zrobić, gdy któryś krok się nie powiedzie.

Istnieją dwa główne style sag: choreography i orchestration.

## Choreography

W choreography nie ma centralnego koordynatora. Każdy kontekst reaguje na zdarzenia i publikuje własne zdarzenia. Sales publikuje `OrderPlaced`. Billing reaguje, wykonuje płatność i publikuje `PaymentCompleted` albo `PaymentFailed`. Inventory reaguje, rezerwuje produkty i publikuje `InventoryReserved` albo `InventoryReservationFailed`. Fulfillment może rozpocząć wysyłkę dopiero wtedy, gdy otrzyma informacje, że płatność i rezerwacja są zakończone.

Zaletą choreografii jest luźne powiązanie. Nie ma jednego centralnego komponentu sterującego całym procesem. Każdy kontekst pozostaje autonomiczny i reaguje na fakty biznesowe.

Wadą jest trudniejsza obserwowalność i kontrola przepływu. Logika procesu jest rozproszona po wielu usługach. Trudniej odpowiedzieć na pytanie, w jakim dokładnie stanie znajduje się zamówienie i który krok powinien nastąpić dalej. Przy prostych procesach choreografia jest bardzo naturalna. Przy bardziej złożonych może stać się trudna do utrzymania.

## Orchestration

W orchestration istnieje koordynator, który steruje procesem. Może to być osobny saga orchestrator albo moduł odpowiedzialny za proces zamówienia. Koordynator wysyła komendę do Billing, czeka na wynik, następnie wysyła komendę do Inventory, a potem do Fulfillment. Jeżeli któryś krok się nie powiedzie, orchestrator uruchamia akcje kompensacyjne.

Zaletą orkiestracji jest większa kontrola. Stan procesu znajduje się w jednym miejscu. Łatwiej śledzić postęp, wykonywać retry, raportować błędy i projektować kompensacje. Jest to dobre rozwiązanie dla procesów z wieloma krokami i skomplikowanymi regułami przejść.

Wadą jest większe ryzyko centralizacji logiki. Orchestrator może stać się zbyt dużym komponentem, który wie za dużo o innych kontekstach. Dlatego powinien wysyłać komendy i reagować na wyniki, ale nie powinien przejmować wewnętrznej logiki Billing, Inventory czy Fulfillment.

## Kompensacje

W systemach opartych o sagi nie wykonuje się klasycznego rollbacku obejmującego wiele baz danych. Zamiast tego stosuje się akcje kompensacyjne. Jeżeli płatność została autoryzowana, ale rezerwacja magazynu się nie udała, system może anulować płatność. Jeżeli magazyn został zarezerwowany, ale wysyłka nie może zostać utworzona, system może zwolnić rezerwację.

Kompensacja nie zawsze oznacza techniczne cofnięcie operacji. Często jest to osobna operacja biznesowa. Anulowanie płatności, zwrot środków, zwolnienie rezerwacji albo anulowanie zamówienia są zdarzeniami biznesowymi, które powinny być modelowane jawnie.

To oznacza, że projektowanie sag wymaga bliskiej współpracy z biznesem. Nie wystarczy zapytać, jak technicznie cofnąć transakcję. Trzeba ustalić, jakie są prawidłowe procesy biznesowe w przypadku częściowej awarii.

## Sieć jako źródło złożoności

Przejście ze wspólnej bazy do komunikacji przez sieć rozwiązuje część problemów sprzężenia, ale wprowadza nowe problemy. Sieć jest zawodna, opóźniona i trudna do przewidzenia. Komunikat może dojść później, może dojść dwa razy albo może nie zostać przetworzony przez pewien czas. Usługa może odpowiadać wolno albo być chwilowo niedostępna.

Dlatego architektura rozproszona wymaga mechanizmów obserwowalności. Potrzebne są logi z correlation ID, metryki, tracing rozproszony, alerty i dashboardy pokazujące stan outboxa, kolejki, błędy konsumentów oraz czas przetwarzania sag. Bez tego debugowanie event-driven architecture jest bardzo trudne.

Wspólna baza ukrywa problemy integracyjne, ale ogranicza skalowalność. Sieć ujawnia problemy integracyjne, ale pozwala budować autonomiczne konteksty. Wybór między tymi podejściami nie jest więc czysto techniczny. Jest to decyzja o tym, gdzie chcemy umieścić złożoność.

## Porównanie podejść

Wspólna baza danych jest prostsza na początku. Umożliwia lokalne transakcje, łatwe joiny i szybki rozwój małego systemu. Jest jednak słaba jako mechanizm integracji między bounded contexts. Prowadzi do coupling, utrudnia niezależne deploymenty i sprawia, że baza staje się centralnym punktem zależności.

Event-driven architecture z osobnymi bazami jest trudniejsza. Wymaga projektowania kontraktów, obsługi eventual consistency, outboxa, idempotentnych konsumentów, retry, sag i monitoringu. W zamian daje lepszą separację, autonomię zespołów, niezależne skalowanie i większą odporność na zmiany wewnętrzne poszczególnych kontekstów.

W małym systemie monolitycznym wspólna baza może być akceptowalna. W modularnym monolicie warto przynajmniej logicznie separować schematy i zakazać dostępu między modułami do cudzych tabel. W mikroserwisach współdzielona baza jest zwykle antywzorcem, ponieważ tworzy rozproszony monolit oparty na jednym schemacie danych.

## Typowe błędy

Jednym z najczęstszych błędów jest wydzielenie mikroserwisów bez wydzielenia danych. Jeżeli usługi są osobnymi procesami, ale nadal korzystają z jednej bazy i tych samych tabel, nie są naprawdę autonomiczne. Powstaje rozproszony monolit.

Drugim błędem jest publikowanie eventów bez outboxa. Taki system może działać poprawnie przez długi czas, ale w momencie awarii ujawnia się luka między zapisem danych a publikacją komunikatu.

Trzecim błędem jest brak idempotentności konsumentów. Jeżeli system zakłada, że wiadomość nigdy nie przyjdzie dwa razy, to wcześniej czy później doprowadzi do błędów biznesowych.

Czwartym błędem jest projektowanie sag bez kompensacji. Proces rozproszony musi mieć odpowiedź na częściowe niepowodzenie. Jeżeli nie wiadomo, co zrobić po nieudanej płatności, nieudanej rezerwacji lub błędzie przewoźnika, architektura jest niekompletna.

Piątym błędem jest nadużywanie komunikacji synchronicznej. Łańcuch wywołań HTTP między wieloma serwisami jest kruchy. Awaria jednego elementu może zablokować cały proces. Dlatego operacje długotrwałe i międzykontekstowe lepiej modelować asynchronicznie.

## Rekomendowane podejście

Najbezpieczniejszym podejściem jest ewolucja od dobrze zorganizowanego modularnego monolitu do architektury event-driven. Na początku można korzystać z jednej fizycznej bazy, ale warto od razu rozdzielać schematy i własność danych. Moduły nie powinny wykonywać zapytań do tabel innych modułów. Komunikacja powinna odbywać się przez porty, zdarzenia wewnętrzne lub jawne API.

Gdy system rośnie, warto wprowadzić outbox i zdarzenia integracyjne. Pozwala to przygotować system do późniejszego wydzielenia usług. Następnie można separować bazy danych i przenosić wybrane konteksty do osobnych procesów. W ten sposób granice logiczne poprzedzają granice fizyczne.

Mikroserwisy i event-driven architecture mają sens wtedy, gdy organizacja jest gotowa obsłużyć dodatkową złożoność. Wymagają automatyzacji, monitoringu, testów kontraktowych, dobrych praktyk operacyjnych i dyscypliny projektowej. Bez tego zamiast elastycznej architektury powstanie system trudniejszy niż monolit.

## Podsumowanie

Integracja i konsystencja są centralnym problemem architektury rozproszonej. Wspólna baza danych upraszcza transakcje, ale łamie izolację bounded contexts i prowadzi do silnego sprzężenia. Event-driven architecture z osobnymi bazami danych wzmacnia autonomię kontekstów, ale wymaga zaakceptowania eventual consistency oraz wdrożenia mechanizmów takich jak transactional outbox, idempotent consumer, retry i sagi.

W większych systemach e-commerce preferowane jest podejście oparte o własność danych i komunikację przez zdarzenia. Każdy kontekst powinien chronić swój model i swoją bazę, a integracja powinna odbywać się przez jawne, wersjonowane kontrakty. Spójność globalna nie jest wtedy wynikiem jednej transakcji, lecz dobrze zaprojektowanego procesu biznesowego rozłożonego na lokalne transakcje.

Najważniejsza zasada brzmi: nie należy używać bazy danych jako ukrytego API między kontekstami. Baza jest szczegółem implementacyjnym właściciela danych. Integracja powinna odbywać się przez kontrakty, zdarzenia i procesy, które są jawne, testowalne i odporne na awarie.
