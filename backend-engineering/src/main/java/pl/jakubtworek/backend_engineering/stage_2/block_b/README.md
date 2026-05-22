# Architektura event-driven dla e-commerce z użyciem Apache Kafka

## Wprowadzenie

Ten dokument opisuje teoretyczne założenia projektowania systemu e-commerce opartego na architekturze event-driven. Przykładowy proces biznesowy obejmuje przepływ od utworzenia zamówienia, przez obsługę płatności, aż po wysyłkę produktu. W takim modelu poszczególne części systemu, takie jak Order Service, Payment Service czy Shipping Service, nie komunikują się ze sobą bezpośrednio przez synchroniczne wywołania HTTP. Zamiast tego wymieniają się zdarzeniami publikowanymi do brokera komunikatów, którym w tym przypadku jest Apache Kafka.

Architektura event-driven dobrze pasuje do systemów rozproszonych, w których poszczególne usługi powinny być możliwie niezależne, skalowalne i odporne na chwilowe awarie innych komponentów. Zdarzenie reprezentuje fakt, który już wystąpił w domenie biznesowej, na przykład `OrderCreated`, `PaymentAuthorized`, `PaymentFailed` albo `ShipmentCreated`. Ważne jest, aby odróżniać zdarzenia od komend. Komenda oznacza żądanie wykonania jakiejś akcji, natomiast zdarzenie informuje, że dana akcja została już wykonana lub dany stan został osiągnięty.

W systemie e-commerce oznacza to, że usługa zamówień może opublikować zdarzenie o utworzeniu zamówienia, a usługa płatności może na nie zareagować bez konieczności bezpośredniego wywoływania Order Service. Dzięki temu usługi są luźniej powiązane. Każda z nich posiada własną logikę biznesową, własny model danych i własną odpowiedzialność. Broker komunikatów pełni rolę pośrednika, który umożliwia asynchroniczną wymianę informacji.

## Model domenowy i zdarzenia

Podstawowy model domenowy składa się z kilku głównych pojęć. Zamówienie reprezentuje intencję klienta zakupu określonych produktów. Płatność opisuje proces autoryzacji lub odrzucenia transakcji finansowej. Wysyłka reprezentuje przygotowanie i przekazanie produktu do dostarczenia. Każdy z tych obszarów może być obsługiwany przez osobny mikroserwis, który przechowuje tylko te dane, za które odpowiada.

Zdarzenia powinny być projektowane jako stabilny kontrakt między usługami. Typowe zdarzenie zawiera metadane oraz właściwy ładunek biznesowy. Do metadanych należą między innymi `eventId`, `occurredAt`, `version` i `correlationId`. Pole `eventId` jednoznacznie identyfikuje zdarzenie i pozwala konsumentom wykrywać duplikaty. Pole `occurredAt` określa moment wystąpienia zdarzenia w domenie, a nie moment jego odczytania przez konsumenta. Pole `version` pomaga zarządzać zmianami kontraktu zdarzenia. `correlationId` pozwala powiązać ze sobą wszystkie zdarzenia należące do jednego procesu biznesowego, na przykład obsługi konkretnego zamówienia.

Ładunek, czyli `payload`, powinien zawierać dane niezbędne konsumentom do wykonania ich pracy. Należy jednak unikać przesadnego umieszczania w zdarzeniu całego stanu encji, jeżeli nie jest to potrzebne. Zdarzenie powinno być wystarczająco kompletne, aby konsument nie musiał natychmiast odpytywać usługi źródłowej, ale jednocześnie nie powinno stawać się niekontrolowanym zrzutem całej bazy danych.

Do definiowania schematów zdarzeń często wykorzystuje się Avro lub Protobuf. Oba formaty pozwalają formalnie opisać strukturę komunikatów, co ułatwia walidację, wersjonowanie i integrację między usługami napisanymi w różnych językach programowania. W praktyce warto używać rejestru schematów, który przechowuje wersje kontraktów i pozwala sprawdzać kompatybilność zmian przed wdrożeniem nowej wersji producenta lub konsumenta.

## Wersjonowanie schematów

Wersjonowanie zdarzeń jest jednym z najważniejszych zagadnień w systemach event-driven. Raz opublikowane zdarzenia mogą być przetwarzane przez wiele usług, również takich, o których producent nie wie w momencie publikacji. Dlatego zmiana schematu nie może być traktowana jak lokalna refaktoryzacja kodu. Jest to zmiana publicznego kontraktu.

Istnieje kilka podejść do wersjonowania. Pierwsze polega na umieszczeniu wersji w nazwie zdarzenia, na przykład `OrderCreatedV1` i `OrderCreatedV2`. To rozwiązanie jest czytelne, ale może prowadzić do mnożenia typów zdarzeń i skomplikowanej logiki po stronie konsumentów. Drugie podejście zakłada użycie pola `version` wewnątrz komunikatu. Konsument może wtedy rozpoznać wersję i odpowiednio zinterpretować payload. Trzecie, często preferowane podejście, polega na kompatybilnej ewolucji schematu. Oznacza to, że nowe pola dodaje się jako opcjonalne albo z wartością domyślną, a istniejących pól nie usuwa się ani nie zmienia ich znaczenia bez kontrolowanej migracji.

Najbezpieczniejsza praktyka polega na zachowaniu zgodności wstecznej. Nowy producent powinien emitować zdarzenia, które nadal mogą zostać odczytane przez starszych konsumentów. Jednocześnie nowi konsumenci powinni być przygotowani na to, że w systemie nadal mogą znajdować się starsze zdarzenia, na przykład zapisane wcześniej w tematach Kafki lub odtwarzane podczas ponownego przetwarzania historii.

Niebezpieczne zmiany to przede wszystkim usuwanie pól, zmiana ich typów, zmiana semantyki istniejącego pola oraz wprowadzanie nowych pól wymaganych bez wartości domyślnej. Nawet pozornie niewielka zmiana, taka jak zmiana znaczenia statusu zamówienia, może spowodować błędy w usługach zależnych. Z tego powodu schemat zdarzenia powinien być traktowany podobnie jak publiczne API.

## Apache Kafka jako broker komunikatów

Apache Kafka jest rozproszoną platformą do przesyłania i przechowywania strumieni zdarzeń. Komunikaty są publikowane do tematów, a tematy dzielą się na partycje. Partycja jest uporządkowanym, dopisywalnym logiem zdarzeń. Kafka nie przechowuje komunikatów w kolejce w tradycyjnym sensie, gdzie komunikat znika po odebraniu przez konsumenta. Zamiast tego komunikaty pozostają w logu przez określony czas lub do osiągnięcia limitu rozmiaru, a konsumenci samodzielnie śledzą swoje położenie za pomocą offsetów.

Ten model ma istotne konsekwencje architektoniczne. Po pierwsze, różne grupy konsumentów mogą niezależnie przetwarzać ten sam temat. Na przykład Payment Service i Analytics Service mogą odczytywać zdarzenia `OrderCreated`, ale każdy z nich posiada własny offset i własne tempo przetwarzania. Po drugie, możliwe jest ponowne przetworzenie historii zdarzeń, jeżeli konsument zresetuje offset lub zostanie uruchomiony nowy proces analityczny.

Kafka zapewnia porządek zdarzeń tylko w obrębie jednej partycji. Dlatego dobór klucza partycjonowania ma duże znaczenie. W przypadku procesu zamówienia naturalnym kluczem jest `orderId`. Jeżeli wszystkie zdarzenia dotyczące tego samego zamówienia są publikowane z tym samym kluczem, trafią do tej samej partycji, a Kafka zachowa ich kolejność. Nie oznacza to jednak globalnego porządku wszystkich zdarzeń w systemie. Zdarzenia dotyczące różnych zamówień mogą być przetwarzane równolegle i w różnej kolejności.

## Wzorzec Outbox

Jednym z podstawowych problemów w systemach event-driven jest zapewnienie spójności między zapisem danych biznesowych a publikacją zdarzenia. Przykładowo Order Service musi zapisać nowe zamówienie w swojej bazie danych oraz opublikować zdarzenie `OrderCreated`. Jeżeli zapis do bazy się powiedzie, ale publikacja do Kafki zakończy się błędem, system znajdzie się w niespójnym stanie: zamówienie istnieje, ale inne usługi się o nim nie dowiedzą. Jeżeli natomiast najpierw opublikujemy zdarzenie, a później zapis do bazy się nie powiedzie, inne usługi mogą zareagować na zamówienie, którego faktycznie nie ma.

Wzorzec Outbox rozwiązuje ten problem przez zapis zdarzenia do specjalnej tabeli w tej samej transakcji, w której zapisywane są dane biznesowe. Oznacza to, że utworzenie zamówienia i dodanie wpisu do tabeli outbox są atomowe z punktu widzenia lokalnej bazy danych. Jeżeli transakcja się powiedzie, oba zapisy istnieją. Jeżeli się nie powiedzie, nie zostanie zapisane ani zamówienie, ani zdarzenie oczekujące na publikację.

Publikacja do Kafki jest wykonywana później przez osobny proces, na przykład cykliczne zadanie, worker lub mechanizm oparty na Change Data Capture. Proces ten odczytuje nieopublikowane wpisy z tabeli outbox, wysyła je do brokera i oznacza jako opublikowane albo usuwa z tabeli. Dzięki temu chwilowa niedostępność Kafki nie powoduje utraty zdarzeń. Zdarzenia pozostają w lokalnej bazie i mogą zostać opublikowane po przywróceniu działania brokera.

Outbox nie eliminuje całkowicie możliwości duplikatów. Proces publikujący może wysłać zdarzenie do Kafki, a następnie ulec awarii przed oznaczeniem wpisu jako opublikowanego. Po restarcie może wysłać to samo zdarzenie ponownie. Dlatego konsumenci nadal muszą być idempotentni. Outbox zapewnia przede wszystkim to, że zdarzenie nie zostanie zgubione, a nie to, że nigdy nie zostanie wysłane więcej niż raz.

## Semantyka dostarczania i idempotencja konsumentów

W praktycznych systemach rozproszonych bardzo trudno osiągnąć semantykę „dokładnie raz” w sensie obejmującym wszystkie efekty uboczne, takie jak zapisy do baz danych, wywołania zewnętrznych API czy wysyłanie wiadomości e-mail. Znacznie bardziej realistycznym założeniem jest dostarczanie „co najmniej raz”. Oznacza to, że zdarzenie powinno zostać dostarczone, ale może zostać dostarczone więcej niż jeden raz.

Z tego powodu każdy konsument powinien być idempotentny. Idempotencja oznacza, że wielokrotne przetworzenie tego samego zdarzenia daje taki sam efekt jak przetworzenie go tylko raz. W typowym rozwiązaniu konsument zapisuje `eventId` każdego poprawnie przetworzonego zdarzenia w tabeli `processed_events`. Przed wykonaniem logiki biznesowej sprawdza, czy dane `eventId` już wystąpiło. Jeżeli tak, pomija przetwarzanie i może bezpiecznie potwierdzić offset. Jeżeli nie, wykonuje efekty uboczne i zapisuje informację o przetworzeniu zdarzenia w tej samej transakcji lokalnej.

Alternatywnym sposobem jest użycie unikalnych indeksów lub operacji typu upsert w tabelach domenowych. Na przykład jeżeli Payment Service tworzy płatność dla danego `orderId`, może wymusić unikalność płatności dla zamówienia. Próba ponownego przetworzenia tego samego zdarzenia nie doprowadzi wtedy do utworzenia drugiej płatności. Warto jednak zachować jawne śledzenie przetworzonych zdarzeń, ponieważ ułatwia ono diagnostykę, audyt i rozwiązywanie problemów.

Offset w Kafce powinien być potwierdzany dopiero po zakończeniu przetwarzania i zapisaniu efektów ubocznych. Jeżeli offset zostanie zatwierdzony zbyt wcześnie, a konsument ulegnie awarii przed zapisem do bazy, zdarzenie może zostać utracone z punktu widzenia logiki biznesowej. Jeżeli offset zostanie zatwierdzony po zapisie, awaria może spowodować ponowne przetworzenie tego samego zdarzenia, ale idempotencja powinna zabezpieczyć system przed zdublowaniem efektów.

## Retry, backoff i DLQ

Nie każdy błąd podczas przetwarzania zdarzenia oznacza błąd trwały. Część awarii ma charakter chwilowy: baza danych może być tymczasowo niedostępna, zewnętrzny dostawca płatności może zwrócić błąd sieciowy, a broker może mieć krótkotrwałe opóźnienia. Dlatego konsumenci powinni posiadać mechanizm ponawiania przetwarzania.

Retry powinien być ograniczony i kontrolowany. Proste natychmiastowe ponawianie może pogorszyć sytuację, szczególnie gdy problem dotyczy przeciążonej usługi zależnej. Lepszym rozwiązaniem jest eksponencjalny backoff, czyli wydłużanie czasu między kolejnymi próbami. Dodatkowo warto dodać jitter, czyli losową składową opóźnienia. Dzięki temu wiele instancji konsumentów nie ponawia pracy dokładnie w tym samym momencie, co zmniejsza ryzyko powstania kolejnej fali przeciążenia.

Po przekroczeniu maksymalnej liczby prób zdarzenie powinno zostać przekazane do DLQ, czyli dead-letter queue lub dead-letter topic. DLQ jest miejscem, w którym trafiają zdarzenia niemożliwe do poprawnego przetworzenia w normalnym trybie. Może to wynikać z błędu danych, nieobsługiwanego typu zdarzenia, niekompatybilnego schematu albo trwałego błędu logiki biznesowej.

DLQ nie powinno być traktowane jako śmietnik, do którego nikt nie zagląda. Jest to ważny element obserwowalności i utrzymania systemu. Zdarzenia w DLQ powinny być monitorowane, analizowane i w miarę możliwości ponownie przetwarzane po usunięciu przyczyny problemu. Rosnący rozmiar DLQ zwykle oznacza poważny problem z jakością danych, zgodnością kontraktów lub stabilnością konsumentów.

## Partycjonowanie i grupy konsumentów

Partycjonowanie w Kafce pozwala skalować przetwarzanie zdarzeń. Temat może mieć wiele partycji, a konsumenci należący do tej samej grupy dzielą między sobą te partycje. W danej grupie jedna partycja jest przetwarzana przez jednego konsumenta naraz. Dzięki temu można równolegle przetwarzać różne partycje, zachowując kolejność w obrębie pojedynczej partycji.

W przypadku e-commerce kluczem partycjonowania powinien być najczęściej `orderId`. Taki wybór sprawia, że zdarzenia dotyczące jednego zamówienia są przetwarzane sekwencyjnie. Jest to ważne, ponieważ przetworzenie `PaymentAuthorized` przed `OrderCreated` mogłoby prowadzić do błędów logicznych. Jednocześnie zdarzenia dotyczące różnych zamówień mogą być obsługiwane równolegle, co pozwala zwiększać przepustowość systemu.

Należy jednak pamiętać, że liczba partycji wpływa na maksymalny poziom równoległości w ramach jednej grupy konsumentów. Jeżeli temat ma cztery partycje, to w jednej grupie efektywnie pracować mogą maksymalnie cztery instancje konsumenta dla tego tematu. Dodatkowe instancje będą bezczynne. Zbyt mała liczba partycji ogranicza skalowanie, a zbyt duża zwiększa narzut operacyjny i może utrudniać zarządzanie klastrami.

Rebalans konsumentów występuje wtedy, gdy zmienia się skład grupy, na przykład po starcie nowej instancji, awarii istniejącej instancji lub zmianie liczby partycji. Podczas rebalansu przetwarzanie może zostać chwilowo wstrzymane. System powinien być na to przygotowany, a projektanci powinni uwzględniać wpływ rebalansów na opóźnienia przetwarzania.

## Eventual consistency

Architektura event-driven zwykle prowadzi do modelu spójności ostatecznej, czyli eventual consistency. Oznacza to, że dane w różnych usługach nie muszą być natychmiast spójne po wykonaniu operacji biznesowej, ale powinny dojść do spójnego stanu po pewnym czasie. W systemie e-commerce zamówienie może zostać utworzone i przez krótki czas znajdować się w stanie `PENDING_PAYMENT`, zanim Payment Service przetworzy zdarzenie i opublikuje wynik autoryzacji płatności.

Taki model wymaga innego sposobu myślenia niż klasyczna transakcja obejmująca wiele tabel w jednej bazie danych. Nie należy zakładać, że wszystkie usługi natychmiast widzą ten sam stan. Każda usługa podejmuje decyzje na podstawie własnych danych oraz zdarzeń, które już zdążyła przetworzyć. Krótkotrwała niespójność jest normalnym elementem działania systemu, a nie wyjątkową sytuacją.

Interfejs użytkownika również musi być zaprojektowany z uwzględnieniem stanów przejściowych. Użytkownik może zobaczyć informację, że zamówienie oczekuje na płatność, płatność jest weryfikowana albo wysyłka jest przygotowywana. Próba ukrycia natury asynchronicznego procesu często prowadzi do gorszego doświadczenia użytkownika i trudniejszych błędów. Lepiej jawnie modelować stany pośrednie i komunikować je w czytelny sposób.

Eventual consistency wymaga także przemyślanego projektowania procesów biznesowych. Usługi powinny być odporne na opóźnienia, ponowne dostarczenia zdarzeń i częściowe awarie. W niektórych przypadkach konieczne są mechanizmy timeoutów, kompensacji lub ręcznej interwencji operatora.

## Saga jako rozproszony proces biznesowy

Saga jest wzorcem służącym do koordynowania procesu biznesowego obejmującego wiele usług, bez użycia jednej globalnej transakcji. W klasycznym systemie monolitycznym można byłoby objąć utworzenie zamówienia, autoryzację płatności i przygotowanie wysyłki jedną transakcją bazodanową. W systemie mikroserwisowym każda usługa posiada własną bazę danych, więc taka transakcja nie jest praktyczna ani pożądana.

Saga składa się z sekwencji lokalnych transakcji. Każda lokalna transakcja jest wykonywana przez jedną usługę i po zakończeniu publikuje zdarzenie, które uruchamia kolejny krok procesu. Jeżeli któryś krok się nie powiedzie, wykonywane są akcje kompensacyjne. Kompensacja nie jest technicznym rollbackiem w sensie bazy danych. Jest osobną operacją biznesową, która odwraca lub neutralizuje skutki wcześniejszego kroku. Przykładowo jeżeli płatność się nie powiedzie, Order Service może anulować zamówienie. Jeżeli wysyłka nie może zostać utworzona po autoryzacji płatności, system może zwrócić środki lub oznaczyć zamówienie do ręcznej obsługi.

Istnieją dwa podstawowe warianty Sagi: choreografia i orkiestracja. W choreografii nie ma centralnego koordynatora. Każda usługa reaguje na zdarzenia i publikuje kolejne zdarzenia. Order Service publikuje `OrderCreated`, Payment Service reaguje i publikuje `PaymentAuthorized` albo `PaymentFailed`, a Shipping Service reaguje na pozytywny wynik płatności. Ten model jest prosty dla niewielkich procesów i dobrze pasuje do luźno powiązanych usług, ale przy większej liczbie kroków może być trudny do zrozumienia, ponieważ logika procesu jest rozproszona po wielu serwisach.

W orkiestracji istnieje centralny komponent, często nazywany Saga Orchestrator, który steruje przebiegiem procesu. Orkiestrator wysyła komendy do poszczególnych usług i czeka na wyniki. Dzięki temu logika procesu jest bardziej jawna i łatwiejsza do monitorowania. Wadą jest większe sprzężenie z orkiestratorem oraz ryzyko, że stanie się on zbyt rozbudowanym centralnym elementem systemu.

Wybór między choreografią a orkiestracją zależy od złożoności procesu. Proste procesy z niewielką liczbą kroków mogą dobrze działać w choreografii. Procesy wymagające wielu warunków, timeoutów, kompensacji i decyzji biznesowych często lepiej modelować przy użyciu orkiestracji.

## Obserwowalność

System event-driven jest trudniejszy do debugowania niż prosty przepływ synchroniczny, ponieważ jedna operacja użytkownika może wywołać wiele asynchronicznych zdarzeń przetwarzanych przez różne usługi w różnym czasie. Dlatego obserwowalność nie jest dodatkiem, lecz podstawowym wymaganiem architektonicznym.

Każde zdarzenie powinno zawierać `correlationId`, który pozwala prześledzić cały proces biznesowy. Dla jednego zamówienia ten sam identyfikator korelacji powinien pojawiać się w logach Order Service, Payment Service, Shipping Service oraz w komunikatach publikowanych do Kafki. Dzięki temu podczas analizy problemu można odtworzyć ścieżkę zdarzeń i zrozumieć, na którym etapie proces się zatrzymał.

Oprócz logów potrzebne są metryki. Szczególnie ważny jest lag konsumentów, czyli różnica między najnowszym offsetem w partycji a offsetem przetworzonym przez konsumenta. Rosnący lag może oznaczać, że konsumenci nie nadążają z przetwarzaniem, wystąpił błąd w logice lub zewnętrzna zależność stała się zbyt wolna. Należy również monitorować liczbę retry, liczbę błędów przetwarzania, rozmiar DLQ, czas przetwarzania zdarzeń oraz liczbę zdarzeń publikowanych przez producentów.

Dobrą praktyką jest także stosowanie rozproszonego tracingu. Pozwala on powiązać działania w różnych usługach w jeden ślad diagnostyczny. W systemach asynchronicznych tracing wymaga konsekwentnego przekazywania kontekstu przez nagłówki komunikatów lub metadane zdarzeń.

## Testowanie odporności

Testowanie systemu event-driven powinno obejmować nie tylko przypadki pozytywne, ale przede wszystkim scenariusze awaryjne. Kluczowe jest sprawdzenie, jak system zachowuje się przy duplikatach, awariach konsumentów, opóźnieniach, niedostępności brokera i błędach schematów.

Jednym z podstawowych testów jest dostarczenie tego samego zdarzenia więcej niż raz. Oczekiwanym wynikiem jest brak zdublowanych efektów biznesowych. Jeżeli `OrderCreated` zostanie dostarczone dwukrotnie, Payment Service nie powinien utworzyć dwóch niezależnych płatności. Taki test weryfikuje idempotencję konsumenta i poprawność użycia `eventId`, unikalnych indeksów albo tabeli `processed_events`.

Kolejny scenariusz to awaria konsumenta w trakcie przetwarzania. Jeżeli konsument zapisze efekt uboczny, ale nie zdąży zatwierdzić offsetu, po restarcie może otrzymać to samo zdarzenie ponownie. System powinien poprawnie rozpoznać, że zdarzenie zostało już obsłużone. Jeżeli konsument ulegnie awarii przed zapisem efektu ubocznego, zdarzenie powinno zostać przetworzone ponownie od początku.

Warto również testować awarię brokera podczas publikacji zdarzeń. W poprawnie zaprojektowanym systemie z Outboxem zdarzenie nie powinno zaginąć, ponieważ najpierw zostało zapisane w lokalnej bazie danych. Po przywróceniu działania brokera proces publikujący powinien wysłać zaległe zdarzenia.

Testy powinny obejmować także błędne dane, niekompatybilne wersje schematów oraz sytuacje, w których zdarzenie trafia do DLQ. Samo istnienie DLQ nie wystarcza. Trzeba sprawdzić, czy zespół operacyjny jest w stanie wykryć problem, zrozumieć jego przyczynę i ponownie przetworzyć komunikaty po naprawie.

## Kompromisy architektoniczne

Architektura event-driven rozwiązuje wiele problemów skalowalności i niezależności usług, ale wprowadza własne koszty. System staje się trudniejszy do zrozumienia, ponieważ przepływ biznesowy nie jest już pojedynczym wywołaniem funkcji ani jedną transakcją. Logika jest rozproszona, a skutki jednej operacji mogą pojawiać się stopniowo w różnych usługach.

Replikacja i asynchroniczna komunikacja wprowadzają opóźnienia. Konsument może przez pewien czas widzieć nieaktualny stan, a widok prezentowany użytkownikowi może nie odzwierciedlać jeszcze wszystkich zakończonych kroków procesu. Trzeba projektować system tak, aby tolerował odczyty nieświeżych danych oraz przejściowe różnice między stanami usług.

Rebalans partycji może tymczasowo zatrzymać konsumowanie, co wpływa na opóźnienia przetwarzania. Wysoka liczba partycji zwiększa możliwości skalowania, ale komplikuje operacje. Z kolei zbyt mała liczba partycji ogranicza równoległość. Dobór liczby partycji i kluczy partycjonowania jest decyzją architektoniczną, której nie należy traktować przypadkowo.

Ewolucja schematów wymaga dyscypliny. W systemie, w którym wiele usług konsumuje te same zdarzenia, nie można swobodnie usuwać pól ani zmieniać ich znaczenia. Konieczne są strategie migracji, okresy przejściowe, kompatybilność wsteczna i testy kontraktowe.

Najważniejszy kompromis dotyczy semantyki przetwarzania. W praktyce obietnica „dokładnie raz” jest często źle rozumiana. Nawet jeżeli broker i klient Kafki oferują mechanizmy transakcyjne, nie rozwiązuje to automatycznie problemu efektów ubocznych wykonywanych poza Kafką. Dlatego solidny projekt powinien opierać się na idempotencji, lokalnych transakcjach, Outboxie, świadomym commitowaniu offsetów i dobrej obserwowalności.

## Podsumowanie

Projektowanie systemu e-commerce w architekturze event-driven wymaga innego sposobu myślenia niż projektowanie klasycznej aplikacji synchronicznej. Zdarzenia stają się kontraktami między usługami, a Kafka pełni rolę trwałego, skalowalnego logu komunikatów. Poszczególne serwisy są luźniej powiązane i mogą rozwijać się niezależnie, ale ceną jest większa złożoność operacyjna i konieczność świadomego projektowania pod kątem awarii.

Najważniejsze zasady to traktowanie schematów zdarzeń jak publicznego API, zapewnienie idempotencji konsumentów, użycie wzorca Outbox po stronie producentów, ręczne zatwierdzanie offsetów po zapisaniu efektów ubocznych oraz projektowanie procesów biznesowych z uwzględnieniem eventual consistency. Saga pozwala modelować rozproszone procesy bez globalnych transakcji, a retry, backoff, jitter i DLQ pomagają obsługiwać błędy w sposób kontrolowany.

Taka architektura nie powinna być wybierana wyłącznie dlatego, że jest popularna. Jej wartość ujawnia się wtedy, gdy system rzeczywiście potrzebuje niezależnego skalowania usług, odporności na chwilowe awarie, asynchronicznego przetwarzania i możliwości rozwoju wielu komponentów w różnym tempie. Najprostsze rozwiązanie, które spełnia wymagania, często jest najlepsze. Jeżeli jednak wybieramy architekturę event-driven, trzeba od początku projektować ją z myślą o duplikatach, opóźnieniach, awariach, ewolucji kontraktów i pełnej obserwowalności.
