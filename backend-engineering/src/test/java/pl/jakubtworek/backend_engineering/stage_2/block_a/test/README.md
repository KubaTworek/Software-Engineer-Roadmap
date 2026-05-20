# Testowanie w DDD, Clean Architecture i architekturze event-driven

## Wprowadzenie

Testowanie systemu zaprojektowanego zgodnie z Domain-Driven Design oraz Clean/Hexagonal Architecture powinno koncentrować się przede wszystkim na zachowaniu biznesowym. Celem testów nie jest sprawdzanie każdego gettera, każdego DTO ani każdego szczegółu frameworka. Najważniejsze jest potwierdzenie, że model domenowy poprawnie realizuje reguły biznesowe, agregaty chronią swoje invariants, serwisy aplikacyjne właściwie orkiestrują przypadki użycia, a adaptery poprawnie tłumaczą świat zewnętrzny na porty aplikacji.

W architekturze DDD logika biznesowa powinna znajdować się w domenie. To oznacza, że najcenniejsze testy to testy agregatów, Value Objects, serwisów domenowych oraz zdarzeń domenowych. Jeżeli do przetestowania reguły biznesowej trzeba uruchamiać bazę danych, serwer HTTP, kontener Springa albo brokera wiadomości, to zwykle oznacza, że logika biznesowa została zbyt mocno związana z infrastrukturą.

Clean Architecture wzmacnia testowalność przez separację portów i adapterów. Warstwa domenowa oraz application layer mogą być testowane w pamięci, z użyciem fake repozytoriów, fake publisherów i prostych implementacji testowych. Adaptery infrastrukturalne są testowane osobno, ponieważ ich celem jest weryfikacja integracji z konkretną technologią, a nie ponowne testowanie reguł biznesowych.


## Testowanie zachowania, a nie struktury

W DDD testujemy zachowanie obiektów domenowych. Oznacza to, że interesuje nas to, co system robi z punktu widzenia biznesu. Nie testujemy klas tylko dlatego, że istnieją. Nie testujemy wszystkich metod tylko dlatego, że są publiczne. Testujemy reguły, decyzje, przejścia stanów, zdarzenia oraz przypadki brzegowe.

Przykładowo dla agregatu `Order` ważne jest to, czy zamówienie można złożyć tylko wtedy, gdy zawiera pozycje, czy suma pozycji zgadza się z oczekiwaną kwotą, czy po złożeniu zamówienia powstaje zdarzenie `OrderPlaced`, oraz czy zamówienia w niedozwolonym stanie nie można ponownie modyfikować. To są zachowania biznesowe.

Nie ma dużej wartości w testowaniu prostego gettera `order.status()` jako osobnego przypadku testowego. Getter jest jedynie sposobem obserwacji stanu. Test powinien sprawdzać, że po wykonaniu operacji biznesowej stan jest poprawny, a nie że getter sam w sobie działa.


## Testy agregatów

Testy agregatów są najważniejszym rodzajem testów w taktycznym DDD. Agregat jest granicą spójności, dlatego to właśnie on powinien chronić invariants i podejmować decyzje biznesowe. Test agregatu powinien być szybki, prosty i całkowicie niezależny od infrastruktury.

Typowy test agregatu składa się z trzech kroków. Najpierw tworzymy agregat w określonym stanie. Następnie wywołujemy operację biznesową, na przykład `order.place()`. Na końcu sprawdzamy wynik: nowy status, wyliczoną sumę, rzucony wyjątek albo wygenerowane zdarzenie domenowe.

Test agregatu nie powinien używać bazy danych. Nie powinien uruchamiać Springa. Nie powinien korzystać z repozytorium. Agregat jest zwykłym obiektem domenowym i powinien dać się przetestować w pełni w pamięci.

Dla operacji złożenia zamówienia testy agregatu powinny obejmować przypadek poprawny, próbę złożenia zamówienia bez pozycji, próbę złożenia zamówienia z błędną sumą oraz próbę modyfikacji zamówienia po jego złożeniu. Warto też sprawdzać, czy agregat generuje odpowiednie zdarzenia domenowe, ponieważ zdarzenia są częścią zachowania systemu.


## Testowanie Value Objects

Value Objects warto testować wtedy, gdy zawierają istotne reguły. Prosty obiekt typu `CustomerId`, który tylko sprawdza pustą wartość, zwykle nie wymaga wielu testów. Natomiast `Money`, `Address`, `Email`, `TaxRate` czy `Quantity` mogą zawierać ważne reguły biznesowe i powinny być testowane.

W przypadku `Money` warto sprawdzić, czy nie można utworzyć ujemnej kwoty, czy nie można dodawać wartości w różnych walutach, czy mnożenie działa poprawnie oraz czy obiekt pozostaje niemutowalny. Takie testy są tanie, szybkie i wartościowe, ponieważ Value Objects często są używane w wielu miejscach systemu.

Dobrze zaprojektowany Value Object zmniejsza liczbę błędów w całym systemie. Jeżeli walidacja adresu e-mail, kwoty lub ilości znajduje się w jednym miejscu, testujemy ją raz i korzystamy z niej wszędzie.


## Testy serwisów domenowych

Serwis domenowy zawiera logikę biznesową, która nie pasuje naturalnie do jednej encji lub jednego agregatu. Może to być polityka cenowa, kalkulacja rabatów, ocena ryzyka, reguły podatkowe lub inny mechanizm domenowy.

Test serwisu domenowego powinien być podobny do testu agregatu: szybki, pamięciowy i niezależny od infrastruktury. Jeżeli serwis domenowy wymaga danych, powinien otrzymać je przez abstrakcje albo gotowe obiekty domenowe. Nie powinien bezpośrednio łączyć się z bazą danych ani z zewnętrznym API.

Warto uważać, aby serwis domenowy nie stał się workiem na całą logikę. Jeżeli testy serwisu domenowego zaczynają obejmować reguły, które naturalnie należą do agregatu, to może być sygnał anemicznego modelu domenowego.


## Testy serwisów aplikacyjnych

Serwis aplikacyjny nie powinien zawierać głównej logiki biznesowej. Jego zadaniem jest orkiestracja przypadku użycia. Dlatego test serwisu aplikacyjnego powinien sprawdzać, czy scenariusz jest poprawnie wykonany: czy agregat został utworzony lub załadowany, czy wywołano odpowiednią metodę domenową, czy zapisano agregat, czy opublikowano zdarzenia oraz czy transakcja obejmuje właściwy zakres.

Do takich testów najlepiej używać fake repozytoriów i fake publisherów. Fake repository to prosta implementacja portu repozytorium działająca w pamięci. Fake publisher zapisuje opublikowane zdarzenia do listy. Dzięki temu można testować use case bez bazy danych, bez brokera wiadomości i bez kontenera aplikacyjnego.

Test use case’a powinien sprawdzać zachowanie z perspektywy aplikacji. Dla `PlaceOrderApplicationService` interesuje nas, czy po wykonaniu komendy istnieje zapisany agregat w stanie `PLACED`, czy został opublikowany event `OrderPlaced`, oraz czy w przypadku błędu biznesowego nic nie zostało zapisane ani opublikowane.


## Fake, mock i stub

W testach architektury portów i adapterów często używa się test doubles. Fake to uproszczona implementacja działająca naprawdę, ale tylko na potrzeby testu. Przykładem jest `InMemoryOrderRepository`, który zapisuje agregaty w mapie. Fake dobrze nadaje się do testowania serwisów aplikacyjnych.

Stub zwraca przygotowane dane. Przykładowo stub polityki cenowej może zawsze zwracać cenę `100 PLN`. Jest przydatny, gdy test nie dotyczy samej polityki, ale potrzebuje jej wyniku.

Mock służy do weryfikacji interakcji. Można nim sprawdzić, czy metoda `publish` została wywołana z eventem `OrderPlaced`. Mocki są użyteczne, ale nadużywane prowadzą do kruchych testów. Jeżeli test przestaje działać po refaktoryzacji, mimo że zachowanie systemu się nie zmieniło, to często znak, że mocki są zbyt mocno związane z implementacją.


## Testy adapterów

Adaptery są zewnętrzną warstwą systemu. Ich zadaniem jest tłumaczenie między technologią a aplikacją. Adapter REST tłumaczy HTTP na komendę aplikacyjną. Adapter JPA tłumaczy agregat domenowy na model persystencji. Adapter Kafka tłumaczy zdarzenie domenowe lub integracyjne na komunikat brokera.

Testy adapterów powinny sprawdzać mapowanie i integrację z technologią. Nie powinny ponownie testować wszystkich reguł biznesowych. Jeżeli agregat został dobrze przetestowany w testach domeny, test adaptera repozytorium nie musi sprawdzać, czy zamówienie bez pozycji jest niedozwolone. Powinien sprawdzić, czy poprawny agregat można zapisać i odczytać.

Adapter persystencji można testować z użyciem H2, Testcontainers albo innej bazy testowej. W bardziej realistycznych systemach lepsze są Testcontainers z tą samą bazą, której używa produkcja, ponieważ H2 potrafi różnić się od PostgreSQL, MySQL czy Oracle. Dla prostych przykładów implementacja in-memory może być wystarczająca, ale nie zastąpi pełnego testu integracyjnego.


## Testy kontraktowe

W architekturze event-driven szczególne znaczenie mają testy kontraktowe. Zdarzenia integracyjne są publicznym kontraktem między bounded contexts. Jeżeli Sales publikuje `OrderPlaced`, to Billing i Fulfillment zależą od struktury tego eventu. Zmiana pola, usunięcie atrybutu albo zmiana znaczenia wartości może uszkodzić downstream.

Test kontraktowy powinien potwierdzać, że publikowane zdarzenie ma oczekiwaną strukturę i jest kompatybilne z konsumentami. W przypadku API REST podobną rolę pełnią testy kontraktowe endpointów. Celem nie jest testowanie całej logiki biznesowej, lecz ochrona granicy komunikacyjnej.

Kontrakty powinny być wersjonowane. Jeżeli event musi się zmienić w sposób niekompatybilny, lepiej wprowadzić nową wersję niż cicho zmienić istniejący payload. Testy kontraktowe pomagają wychwycić takie problemy przed wdrożeniem.


## Testy idempotentnych konsumentów

W systemach opartych o broker wiadomości trzeba zakładać, że ten sam komunikat może zostać dostarczony więcej niż raz. Dlatego konsumenci zdarzeń powinni być idempotentni. Test idempotentnego konsumenta powinien sprawdzać, czy dwukrotne dostarczenie tego samego eventu nie powoduje podwójnego efektu biznesowego.

Przykładowo Fulfillment nie powinien utworzyć dwóch przesyłek po dwukrotnym otrzymaniu tego samego `OrderPlaced`. Billing nie powinien wystawić dwóch faktur dla tego samego zamówienia. Inventory nie powinno podwójnie zarezerwować tego samego stanu magazynowego.

Taki test jest bardzo praktyczny, ponieważ błędy idempotentności w systemach event-driven są częste i kosztowne. Test powinien symulować ponowne dostarczenie tego samego message ID i sprawdzić, że handler wykonał operację tylko raz.


## Testy outboxa

Transactional Outbox jest mechanizmem infrastrukturalnym, ale jego poprawność ma bezpośredni wpływ na spójność systemu. Warto testować, czy zapis zdarzenia do outboxa odbywa się w tej samej transakcji co zapis agregatu, czy relay pobiera nieopublikowane wiadomości, publikuje je i oznacza jako wysłane.

Testy outboxa mogą mieć różne poziomy. Na poziomie jednostkowym można sprawdzić, czy publisher tworzy poprawny rekord outbox. Na poziomie integracyjnym można sprawdzić transakcyjność z bazą danych. Na poziomie systemowym można sprawdzić, czy wiadomość faktycznie trafia do brokera.

Ważne jest również testowanie scenariuszy błędów. Jeżeli publikacja do brokera się nie uda, wiadomość nie powinna zostać oznaczona jako opublikowana. Jeżeli publikacja się uda, ale proces padnie przed oznaczeniem rekordu, system powinien bezpiecznie ponowić wysłanie, a konsument powinien obsłużyć potencjalny duplikat.


## Testy sag

Saga koordynuje proces rozproszony, dlatego wymaga osobnego podejścia testowego. Test sagi powinien sprawdzać przejścia stanów oraz reakcje na zdarzenia. Jeżeli przychodzi `OrderPlaced`, saga powinna rozpocząć proces. Jeżeli przychodzi `PaymentCompleted`, powinna przejść do kolejnego kroku. Jeżeli przychodzi `InventoryReservationFailed`, powinna uruchomić kompensację.

W przypadku sagi choreograficznej testuje się poszczególne handlery eventów i ich wpływ na lokalny stan procesu. W przypadku sagi orkiestracyjnej testuje się orchestrator, jego stan oraz komendy wysyłane do innych kontekstów.

Szczególnie ważne są testy ścieżek błędów. Proces zamówienia zwykle działa dobrze w ścieżce szczęśliwej, ale prawdziwa wartość sagi ujawnia się przy częściowych awariach. Testy powinny obejmować nieudaną płatność, brak stanu magazynowego, timeout, ponowienie wiadomości i kompensację.


## Testy end-to-end

Testy end-to-end sprawdzają cały przepływ przez wiele warstw lub wiele usług. Są wartościowe, ale powinny być używane oszczędnie. Są wolniejsze, bardziej kruche i trudniejsze w diagnozowaniu niż testy domenowe lub aplikacyjne.

W systemie e-commerce test end-to-end może sprawdzić, czy po złożeniu zamówienia pojawia się płatność, rezerwacja magazynu i zaplanowana wysyłka. Taki test daje pewność, że integracja działa, ale nie powinien zastępować testów agregatów, use case’ów, adapterów i kontraktów.

Dobra strategia testowa przypomina piramidę. Najwięcej powinno być szybkich testów jednostkowych domeny i application layer. Mniej powinno być testów integracyjnych adapterów. Najmniej powinno być pełnych testów end-to-end.


## Czego nie testować

Nie warto pisać osobnych testów dla prostych getterów, setterów, rekordów, prostych DTO i klas bez logiki. Takie testy zwiększają liczbę kodu testowego, ale nie dają realnej ochrony przed błędami.

Nie warto testować frameworka. Jeżeli Spring MVC mapuje JSON na DTO w standardowy sposób, zwykle nie trzeba pisać testu tylko po to, aby potwierdzić, że biblioteka działa. Test ma sens wtedy, gdy istnieje własna logika mapowania, niestandardowa konfiguracja albo publiczny kontrakt wymagający stabilności.

Nie warto testować implementacyjnych detali, które mogą się zmienić przy refaktoryzacji. Test powinien dawać swobodę zmiany kodu, o ile zachowanie biznesowe pozostaje takie samo. Jeżeli testy masowo psują się po poprawie struktury kodu bez zmiany zachowania, prawdopodobnie są zbyt mocno związane z implementacją.


## Testowanie a architektura

Testowalność jest jednym z najlepszych wskaźników jakości architektury. Jeżeli domeny nie da się przetestować bez bazy danych, oznacza to, że domena zależy od infrastruktury. Jeżeli use case’a nie da się przetestować bez HTTP, oznacza to, że application layer zna adapter wejściowy. Jeżeli test wymaga uruchomienia całego systemu dla prostej reguły biznesowej, granice warstw są najprawdopodobniej naruszone.

Dobra architektura pozwala testować warstwy osobno. Domena jest testowana jako czysty model biznesowy. Application layer jest testowany przez porty i fake implementacje. Adaptery są testowane jako integracje z konkretnymi technologiami. Kontrakty są testowane na granicach komunikacji. End-to-end potwierdza, że całość działa razem, ale nie zastępuje testów niższych poziomów.

W praktyce dobrze zaprojektowany system ma mniej testów wymagających uruchomienia pełnej aplikacji. Większość błędów biznesowych można złapać szybciej i taniej na poziomie agregatów oraz serwisów aplikacyjnych.


## Przykładowa strategia testowania

Dla przypadku `PlaceOrder` strategia testowania mogłaby wyglądać następująco. Najpierw testujemy agregat `Order`: poprawne złożenie zamówienia, błędną sumę, brak pozycji, niedozwoloną zmianę stanu oraz wygenerowanie eventu `OrderPlaced`.

Następnie testujemy `PlaceOrderApplicationService`: poprawną orkiestrację, zapis agregatu, publikację eventu oraz brak zapisu i publikacji przy błędzie biznesowym. Używamy fake repozytorium, fake publishera i fake transaction managera.

Potem testujemy adapter repozytorium: czy agregat jest poprawnie mapowany i zapisywany do bazy. Ten test może używać prawdziwej bazy testowej. Następnie testujemy adapter HTTP: czy żądanie jest mapowane na komendę i czy wynik use case’a jest mapowany na odpowiedź.

Jeżeli event `OrderPlaced` jest publikowany do innych kontekstów, dodajemy test kontraktowy dla jego struktury. Jeżeli event jest konsumowany przez Billing lub Fulfillment, testujemy idempotentność konsumentów. Jeżeli w procesie występuje saga, testujemy przejścia stanów i kompensacje.


## Podsumowanie

Testowanie w DDD i Clean Architecture powinno koncentrować się na zachowaniu biznesowym oraz granicach architektury. Największą wartość mają testy agregatów, Value Objects, serwisów domenowych i serwisów aplikacyjnych. To one chronią reguły biznesowe i pozwalają szybko wykrywać błędy bez uruchamiania infrastruktury.

Adaptery, baza danych, HTTP, Kafka i outbox powinny być testowane osobno jako elementy integracyjne. Nie należy mieszać testów domeny z testami infrastruktury, ponieważ prowadzi to do wolnych i kruchych testów.

W systemach event-driven szczególnie ważne są testy kontraktowe, testy idempotentnych konsumentów, testy outboxa i testy sag. To one chronią przed błędami typowymi dla komunikacji asynchronicznej: duplikatami, opóźnieniami, częściowymi awariami i niekompatybilnymi kontraktami.

Najważniejsza zasada brzmi: testujemy zachowanie, nie strukturę. Dobrze napisany test mówi, jaką regułę biznesową chroni. Jeżeli test nie daje takiej informacji, warto zastanowić się, czy naprawdę jest potrzebny.
