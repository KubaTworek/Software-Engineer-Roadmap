# Model domenowy i kontrakty eventów w architekturze event-driven

## Wprowadzenie

Ten dokument opisuje teoretyczne podstawy projektowania modelu domenowego oraz kontraktów zdarzeń w przykładowym systemie e-commerce opartym o architekturę event-driven. Przykład dotyczy typowego przepływu biznesowego, w którym klient składa zamówienie, system próbuje zautoryzować płatność, a po jej powodzeniu inicjowana jest wysyłka. Chociaż sam przypadek jest prosty, dobrze pokazuje najważniejsze decyzje projektowe związane z komunikacją asynchroniczną, granicami kontekstowymi, stabilnością kontraktów oraz odpowiedzialnością poszczególnych serwisów.

Architektura event-driven zakłada, że system nie jest zbudowany jako jeden centralny proces wykonujący wszystkie kroki synchronicznie. Zamiast tego poszczególne części systemu reagują na zdarzenia, które informują, że w domenie biznesowej coś już się wydarzyło. Zdarzenie nie jest poleceniem ani żądaniem wykonania akcji. Jest faktem historycznym. Oznacza to, że nazwa zdarzenia powinna być formułowana w czasie przeszłym, na przykład `OrderPlaced`, `PaymentAuthorized`, `PaymentFailed` albo `ShippingInitiated`. Taka konwencja pomaga utrzymać poprawny model mentalny: producent zdarzenia nie mówi innemu serwisowi, co ma zrobić, lecz publikuje informację o zmianie stanu lub ważnym fakcie biznesowym.

## Granice kontekstowe

W przykładowym systemie e-commerce można wyróżnić trzy główne granice kontekstowe: `Order Service`, `Payment Service` oraz `Shipping Service`. Każda z tych granic odpowiada za własny fragment domeny i powinna posiadać własny model danych, własne reguły biznesowe oraz własną bazę danych. Taki podział jest zgodny z podejściem Domain-Driven Design, w którym nie próbuje się tworzyć jednego, uniwersalnego modelu całego systemu. Zamiast tego każdy kontekst ma swój język, swoje pojęcia i własną odpowiedzialność.

`Order Service` odpowiada za przyjmowanie i zarządzanie zamówieniami. To w tym serwisie powstaje zamówienie, które może znajdować się w różnych stanach, takich jak oczekiwanie na płatność, potwierdzenie, anulowanie albo wysłanie. `Payment Service` odpowiada za obsługę płatności. Jego zadaniem nie jest zarządzanie całym zamówieniem, lecz przeprowadzenie próby płatności i opublikowanie wyniku tej próby. `Shipping Service` odpowiada za proces wysyłki, czyli za przygotowanie i obsługę dostarczenia zamówienia po spełnieniu warunków biznesowych, takich jak poprawnie autoryzowana płatność.

Ważne jest, aby te serwisy nie współdzieliły bezpośrednio swoich tabel bazodanowych ani wewnętrznych klas domenowych. Encja `Order` z `Order Service` nie powinna być używana jako model w `Payment Service`. Podobnie wewnętrzna reprezentacja płatności nie powinna być narzucana serwisowi zamówień. Komunikacja między kontekstami powinna odbywać się przez jawne kontrakty integracyjne, czyli zdarzenia. Dzięki temu każdy serwis może rozwijać swój model wewnętrzny niezależnie, o ile nadal dotrzymuje publicznego kontraktu zdarzeń.

## Zdarzenia jako fakty biznesowe

Zdarzenie domenowe opisuje coś, co już zaszło w systemie z punktu widzenia biznesu. `OrderPlaced` oznacza, że klient złożył zamówienie. `PaymentAuthorized` oznacza, że płatność została poprawnie autoryzowana. `PaymentFailed` oznacza, że próba płatności zakończyła się niepowodzeniem. `ShippingInitiated` oznacza, że proces wysyłki został rozpoczęty. Każde z tych zdarzeń jest informacją o stanie świata biznesowego w określonym momencie.

Taka interpretacja ma istotne konsekwencje projektowe. Zdarzenie powinno być niemodyfikowalne. Po opublikowaniu nie należy go zmieniać, ponieważ reprezentuje fakt historyczny. Jeżeli później okaże się, że wcześniejsze zdarzenie prowadziło do innego rezultatu, system powinien opublikować kolejne zdarzenie korygujące, na przykład `OrderCancelled`, a nie edytować poprzedniego komunikatu. W architekturach zdarzeniowych historia zdarzeń jest istotna, ponieważ pozwala odtworzyć przebieg procesu, diagnozować błędy oraz analizować zachowanie systemu.

Zdarzenia nie powinny być projektowane jako techniczne komunikaty zależne od aktualnej implementacji jednego serwisu. Powinny odzwierciedlać język domeny. Nazwa `PaymentAuthorized` jest lepsza niż `UpdateOrderStatusMessage`, ponieważ pierwsza mówi o fakcie biznesowym, a druga zdradza techniczny zamiar względem innego komponentu. Zdarzenie powinno być zrozumiałe dla osób pracujących z domeną, nie tylko dla programistów.

## Kontrakt eventu

Każde zdarzenie powinno mieć dobrze zdefiniowany kontrakt. Kontrakt określa, jakie pola zawiera zdarzenie, jakie mają typy, jakie znaczenie biznesowe oraz jakie reguły kompatybilności obowiązują przy jego zmianach. W praktyce kontrakty zdarzeń często opisuje się za pomocą formatów takich jak Avro, Protobuf albo JSON Schema. Sam format jest mniej istotny niż dyscyplina projektowa: zdarzenie powinno być jawne, wersjonowane i stabilne.

Typowe zdarzenie składa się z dwóch części: metadanych oraz payloadu. Metadane opisują samo zdarzenie. Payload opisuje dane biznesowe. Taki podział pomaga oddzielić informacje techniczne, potrzebne do śledzenia, wersjonowania i przetwarzania komunikatu, od informacji domenowych, które są istotne dla logiki biznesowej.

Do metadanych zwykle należą `eventId`, `occurredAt`, `version`, `correlationId`, `causationId` oraz `sourceService`. `eventId` jest unikalnym identyfikatorem konkretnego zdarzenia. Ma znaczenie praktyczne, ponieważ konsument może używać go do zapewnienia idempotencji i wykrywania duplikatów. `occurredAt` określa moment wystąpienia zdarzenia w serwisie źródłowym. Nie należy mylić go z momentem publikacji do brokera ani z momentem odebrania przez konsumenta. W systemach rozproszonych te trzy czasy mogą się różnić.

Pole `version` wskazuje wersję kontraktu zdarzenia. Jest ono przydatne, gdy zdarzenie ewoluuje w czasie. `correlationId` pozwala powiązać wiele zdarzeń należących do tego samego przepływu biznesowego. W prostym przypadku może być równe `orderId`, ale w bardziej złożonych systemach może być oddzielnym identyfikatorem sagi lub trace ID. `causationId` wskazuje zdarzenie, które doprowadziło do powstania bieżącego zdarzenia. Dzięki temu można odtworzyć nie tylko korelację, ale również zależność przyczynowo-skutkową między komunikatami. `sourceService` informuje, który serwis opublikował zdarzenie.

Payload zawiera dane domenowe. Dla `OrderPlaced` będą to na przykład `orderId`, lista pozycji zamówienia oraz całkowita kwota. Dla `PaymentAuthorized` będą to `orderId`, `paymentId`, kwota oraz status płatności. Dla `PaymentFailed` istotny będzie także powód niepowodzenia. Dla `ShippingInitiated` kluczowe są `orderId` oraz `shipmentId`. Projektując payload, należy zachować równowagę. Zdarzenie powinno zawierać wystarczająco dużo informacji, aby konsumenci nie musieli natychmiast odpytywać producenta o szczegóły, ale nie powinno być bezrefleksyjną kopią całej encji domenowej.

## Encje domenowe a kontrakty integracyjne

Jednym z częstych błędów w projektowaniu systemów event-driven jest utożsamianie modelu domenowego z kontraktem zdarzenia. Encja `Order` używana wewnątrz `Order Service` nie powinna automatycznie stawać się payloadem komunikatu `OrderPlaced`. Model domenowy jest prywatny dla danego serwisu i może zmieniać się wraz z rozwojem implementacji. Kontrakt zdarzenia jest natomiast publicznym API dla innych części systemu. Zmiana kontraktu może wpłynąć na wielu konsumentów, dlatego powinna być wykonywana ostrożnie.

W praktyce warto traktować klasy zdarzeń jako osobny model integracyjny. Nawet jeśli na początku pola encji i eventu są podobne, powinny istnieć oddzielne klasy lub schematy. Pozwala to uniknąć przypadkowego ujawniania szczegółów implementacyjnych oraz ogranicza ryzyko, że refaktoryzacja wewnętrznego modelu domenowego zepsuje komunikację między serwisami. Mapa między encją a zdarzeniem powinna być świadomą decyzją projektową, a nie efektem automatycznej serializacji obiektu domenowego.

To rozróżnienie ma duże znaczenie przy długotrwałej ewolucji systemu. Wewnętrzna encja może zostać przebudowana, rozbita na kilka klas albo wzbogacona o pola techniczne. Nie oznacza to, że każde takie pole powinno trafić do eventu. Zdarzenie powinno zawierać tylko te informacje, które mają sens jako publiczny fakt biznesowy i mogą być użyte przez innych uczestników procesu.

## Przykładowy przepływ zdarzeń

Typowy przepływ rozpoczyna się od utworzenia zamówienia. `Order Service` zapisuje zamówienie w swojej bazie danych i publikuje zdarzenie `OrderPlaced`. To zdarzenie oznacza, że zamówienie zostało przyjęte, ale nie musi jeszcze oznaczać, że zostanie zrealizowane. Zamówienie może znajdować się w stanie pośrednim, na przykład `PENDING_PAYMENT`, ponieważ system oczekuje na wynik płatności.

`Payment Service` konsumuje zdarzenie `OrderPlaced` i rozpoczyna obsługę płatności. Jeżeli płatność zostanie poprawnie autoryzowana, publikuje `PaymentAuthorized`. Jeżeli próba zakończy się błędem, publikuje `PaymentFailed`. Warto zauważyć, że `Payment Service` nie powinien bezpośrednio zmieniać tabeli zamówień w bazie `Order Service`. Jego odpowiedzialnością jest płatność, a nie stan zamówienia. To `Order Service`, jako właściciel zamówienia, powinien zareagować na wynik płatności i zmienić stan zamówienia we własnej bazie.

Po zdarzeniu `PaymentAuthorized` system może przejść do wysyłki. `Shipping Service` konsumuje informację o autoryzowanej płatności i inicjuje proces realizacji zamówienia. Następnie publikuje `ShippingInitiated` albo późniejsze zdarzenie `OrderShipped`, zależnie od tego, jak szczegółowo modelowany jest proces logistyczny. Równolegle `Order Service` może również konsumować `PaymentAuthorized` i zmienić status zamówienia na `CONFIRMED`.

Jeżeli pojawi się `PaymentFailed`, system powinien uruchomić logikę kompensacyjną. Najprostszym wariantem jest anulowanie zamówienia przez `Order Service` i opublikowanie zdarzenia `OrderCancelled`. Nie oznacza to klasycznej transakcji rozproszonej w sensie natychmiastowego atomowego zatwierdzania wszystkich zmian. Jest to raczej sekwencja lokalnych decyzji, które stopniowo doprowadzają system do spójnego stanu.

## Eventual consistency

W architekturze event-driven naturalnym modelem spójności jest eventual consistency, czyli spójność osiągana z opóźnieniem. Oznacza to, że w danej chwili różne serwisy mogą widzieć różne etapy tego samego procesu. Zamówienie może istnieć w `Order Service`, ale `Payment Service` może jeszcze nie przetworzyć zdarzenia `OrderPlaced`. Płatność może być już autoryzowana, ale `Shipping Service` może jeszcze nie rozpocząć wysyłki. Taka krótkotrwała niespójność nie jest błędem, tylko cechą architektury asynchronicznej.

Projektując taki system, trzeba jawnie modelować stany pośrednie. Zamówienie nie powinno od razu przyjmować statusu końcowego. Stan `PENDING_PAYMENT` jest naturalny, ponieważ odzwierciedla oczekiwanie na wynik innego procesu. Podobnie mogą istnieć stany takie jak `CONFIRMED`, `CANCELLED` czy `SHIPPED`. Interfejs użytkownika również powinien rozumieć te stany i nie zakładać, że każda operacja kończy się natychmiastowym, globalnie spójnym rezultatem.

Eventual consistency wymaga innego podejścia do projektowania niż system synchroniczny. Nie można zakładać, że po wykonaniu jednej operacji wszystkie widoki i serwisy natychmiast pokażą ten sam stan. Zamiast tego trzeba projektować procesy odporne na opóźnienia, ponowienia, duplikaty i chwilową niedostępność części systemu. W zamian system zyskuje większą niezależność komponentów, lepszą skalowalność i większą odporność na awarie częściowe.

## Idempotencja i duplikaty

W praktycznych systemach opartych o brokery komunikatów konsumenci powinni zakładać, że to samo zdarzenie może zostać dostarczone więcej niż raz. Nawet jeśli broker oferuje silne gwarancje, awarie konsumenta, problemy z commitowaniem offsetów albo ponowne próby mogą doprowadzić do ponownego przetworzenia tego samego komunikatu. Dlatego konsument musi być idempotentny.

Idempotencja oznacza, że wielokrotne przetworzenie tego samego zdarzenia daje taki sam efekt jak przetworzenie go jeden raz. W przypadku zdarzeń można to osiągnąć przez zapisywanie `eventId` w tabeli przetworzonych zdarzeń albo przez użycie unikalnych ograniczeń w bazie danych. Konsument przed wykonaniem efektów ubocznych sprawdza, czy dany `eventId` był już obsłużony. Jeżeli tak, pomija zdarzenie. Jeżeli nie, wykonuje logikę biznesową i zapisuje informację o przetworzeniu.

To podejście jest zwykle bardziej realistyczne niż próba zbudowania idealnego przetwarzania exactly-once we wszystkich warstwach systemu. W praktyce dokładnie jednokrotne przetworzenie jest trudne, szczególnie gdy komunikat powoduje efekty uboczne w bazach danych, zewnętrznych API albo systemach płatniczych. Dlatego dobry projekt event-driven opiera się na lokalnych transakcjach, idempotencji i świadomym radzeniu sobie z ponowieniami.

## Korelacja i obserwowalność

W systemie rozproszonym bardzo ważna jest możliwość śledzenia przepływu zdarzeń. Gdy klient zgłasza problem z zamówieniem, zespół techniczny powinien móc sprawdzić, jakie zdarzenia zostały opublikowane, które serwisy je przetworzyły i gdzie ewentualnie wystąpiło opóźnienie albo błąd. Do tego służy między innymi `correlationId`.

W prostym systemie `correlationId` może być równy `orderId`, ponieważ wszystkie zdarzenia dotyczące danego zamówienia naturalnie łączą się wokół tego identyfikatora. W bardziej zaawansowanych systemach lepiej używać oddzielnego identyfikatora procesu, sagi albo trace ID. Dzięki temu jeden proces biznesowy może obejmować wiele agregatów, a nadal być możliwy do prześledzenia jako całość.

`causationId` uzupełnia tę informację, wskazując bezpośrednią przyczynę powstania zdarzenia. Jeżeli `PaymentAuthorized` powstało w reakcji na `OrderPlaced`, to jego `causationId` może wskazywać `eventId` zdarzenia `OrderPlaced`. W ten sposób można odtworzyć nie tylko to, które zdarzenia należą do jednego procesu, ale również jakie były zależności przyczynowe między nimi.

Obserwowalność nie kończy się na identyfikatorach. System powinien monitorować opóźnienia konsumentów, liczbę błędów, liczbę ponowień, rozmiar kolejek dead-letter oraz czas przetwarzania zdarzeń. Bez takich metryk architektura event-driven szybko staje się trudna do diagnozowania, ponieważ problem może nie być widoczny jako jeden oczywisty wyjątek w jednym miejscu, lecz jako narastające opóźnienie między serwisami.

## Wersjonowanie kontraktów

Zdarzenia są publicznym API systemu, dlatego ich zmiany wymagają ostrożności. Najbezpieczniejszą strategią jest kompatybilna ewolucja schematu. Oznacza to, że nowe pola dodaje się jako opcjonalne albo z wartością domyślną, nie usuwa się pól bez planu migracji i nie zmienia się znaczenia istniejących pól. Konsumenci mogą wtedy działać dalej, nawet jeśli producent zacznie publikować bogatszą wersję zdarzenia.

Pole `version` może pomagać w rozróżnianiu wariantów kontraktu, ale samo w sobie nie rozwiązuje problemu kompatybilności. Jeżeli producent opublikuje nową wersję, której stary konsument nie potrafi odczytać, system nadal się zepsuje. Dlatego wersjonowanie powinno być połączone z zasadami zgodności wstecznej i z testami kontraktowymi.

Czasami stosuje się osobne nazwy zdarzeń dla dużych zmian, na przykład `OrderPlacedV2`. Taka strategia bywa użyteczna, gdy zmiana jest przełomowa i nie da się jej sensownie wyrazić jako kompatybilnej ewolucji. Trzeba jednak pamiętać, że mnożenie wersji zdarzeń komplikuje system. Najczęściej lepiej projektować pierwsze kontrakty ostrożnie i rozwijać je w sposób zgodny wstecznie.

## Granularność zdarzeń

Projektując eventy, trzeba zdecydować, jak szczegółowe powinny być zdarzenia. Zbyt ogólne zdarzenia, takie jak `OrderUpdated`, są mało użyteczne, ponieważ nie mówią, co naprawdę wydarzyło się w domenie. Konsument musi wtedy analizować różnice w payloadzie albo znać wewnętrzną logikę producenta. Zbyt szczegółowe zdarzenia mogą z kolei powodować nadmierny szum i utrudniać zrozumienie procesu.

Dobrą praktyką jest nazywanie zdarzeń według znaczących faktów biznesowych. `OrderPlaced`, `PaymentAuthorized`, `PaymentFailed` i `OrderCancelled` są przykładami zdarzeń, które mają jasne znaczenie. Każde z nich może uruchamiać inną reakcję w systemie. Ich znaczenie jest stabilniejsze niż techniczne operacje typu aktualizacja rekordu.

Granularność powinna wynikać z domeny, a nie z wygody implementacji. Jeżeli dana zmiana stanu ma znaczenie biznesowe, audytowe albo integracyjne, prawdopodobnie zasługuje na własne zdarzenie. Jeżeli jest wyłącznie wewnętrznym detalem algorytmu, zwykle nie powinna być publikowana jako publiczny event.

## Partycjonowanie i kolejność

W systemach wykorzystujących Kafka ważne jest dobranie klucza partycjonowania. Jeżeli wszystkie zdarzenia dotyczące tego samego zamówienia są publikowane z kluczem `orderId`, trafią do tej samej partycji. Dzięki temu Kafka zachowa ich kolejność w obrębie tego klucza. To istotne, ponieważ dla jednego zamówienia `OrderPlaced` powinno logicznie poprzedzać `PaymentAuthorized`, a autoryzacja płatności powinna poprzedzać inicjację wysyłki.

Nie oznacza to jednak, że Kafka zapewnia globalną kolejność wszystkich zdarzeń w systemie. Kolejność jest zachowywana w obrębie partycji, a więc w praktyce w obrębie danego klucza. To wystarcza dla wielu przypadków biznesowych, ponieważ zwykle zależy nam na poprawnej sekwencji zdarzeń dla jednego agregatu, a nie dla całego sklepu internetowego.

Dobór klucza partycjonowania jest kompromisem. Użycie `orderId` dobrze wspiera kolejność zdarzeń zamówienia, ale jeżeli jeden klucz generowałby bardzo dużo ruchu, mógłby stać się wąskim gardłem. W typowym e-commerce rozkład zamówień jest jednak naturalnie szeroki, więc `orderId` jest rozsądnym wyborem.

## Saga i kompensacje

Przepływ `Order → Payment → Shipping` jest przykładem procesu, który przekracza granice jednego serwisu. W klasycznym monolicie można by próbować objąć wszystko jedną transakcją. W systemie mikroserwisowym i event-driven zwykle nie jest to dobre rozwiązanie. Każdy serwis posiada własną bazę i wykonuje lokalne transakcje. Cały proces jest koordynowany przez zdarzenia oraz reakcje na te zdarzenia.

Taki proces można opisać jako sagę. Saga jest sekwencją lokalnych transakcji, które razem realizują większy cel biznesowy. Jeżeli któryś krok się nie powiedzie, system wykonuje kompensację. W omawianym przykładzie nieudana płatność może spowodować anulowanie zamówienia. Kompensacja nie jest cofnięciem czasu ani technicznym rollbackiem wszystkich baz danych. Jest nową akcją biznesową, która przywraca system do akceptowalnego stanu.

Saga może być realizowana choreograficznie albo orkiestracyjnie. W choreografii nie ma jednego centralnego kontrolera. Każdy serwis reaguje na zdarzenia i publikuje kolejne. `Payment Service` reaguje na `OrderPlaced`, a `Shipping Service` reaguje na `PaymentAuthorized`. Ten model jest luźno powiązany, ale przy większej liczbie kroków może być trudniejszy do zrozumienia, ponieważ logika procesu jest rozproszona.

W orkiestracji istnieje komponent nadrzędny, który steruje przebiegiem procesu. Orkiestrator wysyła polecenia albo oczekuje na wyniki kolejnych kroków. Taki model bywa łatwiejszy do monitorowania i kontrolowania, ale wprowadza dodatkowy komponent centralny, który zna przebieg procesu. Wybór między choreografią a orkiestracją zależy od złożoności procesu, wymagań obserwowalności i akceptowanego poziomu sprzężenia.

## Outbox jako sposób publikowania zdarzeń

Publikowanie zdarzeń powinno być spójne z zapisem stanu biznesowego. Jeżeli `Order Service` zapisze zamówienie w bazie, ale nie opublikuje `OrderPlaced` z powodu awarii brokera, inne serwisy nigdy nie dowiedzą się o zamówieniu. Jeżeli natomiast najpierw opublikuje event, a potem nie uda się zapisać zamówienia, system otrzyma zdarzenie o fakcie, który w bazie producenta nie istnieje. Oba przypadki są niebezpieczne.

Wzorzec Outbox rozwiązuje ten problem przez zapis biznesowy i zapis komunikatu w jednej lokalnej transakcji bazodanowej. Serwis tworzy zamówienie i jednocześnie zapisuje rekord w tabeli outbox. Dopiero osobny proces odczytuje outbox i publikuje komunikat do brokera. Po udanej publikacji rekord może zostać oznaczony jako wysłany. Dzięki temu lokalna baza danych staje się źródłem prawdy o tym, jakie zdarzenia powinny zostać opublikowane.

Outbox nie eliminuje potrzeby idempotencji po stronie konsumentów. Proces publikujący może po awarii wysłać ten sam event ponownie. Dlatego `eventId` nadal jest konieczny. W praktyce Outbox i idempotentni konsumenci uzupełniają się: Outbox chroni przed utratą zdarzeń po stronie producenta, a idempotencja chroni przed skutkami duplikatów po stronie konsumenta.

## Retry i dead-letter queue

W systemach rozproszonych część błędów ma charakter przejściowy. Zewnętrzne API może chwilowo nie odpowiadać, baza danych może być przeciążona, a sieć może mieć krótkie problemy. Dlatego konsument nie powinien od razu porzucać zdarzenia po pierwszym niepowodzeniu. Typowym rozwiązaniem jest mechanizm retry, czyli ponawianie próby przetworzenia.

Retry powinien być ograniczony i kontrolowany. Często stosuje się eksponencjalny backoff, czyli coraz dłuższe odstępy między próbami, oraz jitter, czyli losowe odchylenie czasu oczekiwania. Jitter zapobiega sytuacji, w której wiele konsumentów ponawia operację dokładnie w tym samym momencie, powodując kolejną falę przeciążenia.

Jeżeli zdarzenia nie udaje się przetworzyć po określonej liczbie prób, powinno trafić do dead-letter queue. DLQ nie jest miejscem na ignorowanie błędów, lecz mechanizmem separacji problematycznych komunikatów. Dzięki temu pojedynczy wadliwy event nie blokuje całego strumienia. Zespół może później przeanalizować wiadomości w DLQ, poprawić błąd i ewentualnie ponownie wprowadzić je do przetwarzania.

## Projektowanie payloadu

Payload zdarzenia powinien być projektowany z perspektywy konsumentów, ale bez uzależniania producenta od konkretnej implementacji konsumenta. To delikatna równowaga. Z jednej strony zbyt ubogi payload zmusza inne serwisy do synchronicznych zapytań do producenta, co osłabia zalety architektury event-driven. Z drugiej strony zbyt bogaty payload może ujawniać zbyt wiele szczegółów i zwiększać koszt utrzymania kontraktu.

Dla `OrderPlaced` sensowne jest przekazanie identyfikatora zamówienia, listy pozycji i kwoty. `Payment Service` może potrzebować tych informacji, aby rozpocząć płatność. Warto jednak rozważyć, czy event powinien zawierać wszystkie dane klienta, adres wysyłki, rabaty, historię cen i wewnętrzne flagi. Nie każde pole z encji zamówienia jest automatycznie częścią publicznego faktu biznesowego.

Dobrą praktyką jest traktowanie payloadu jako migawki istotnych informacji w momencie wystąpienia zdarzenia. Jeżeli cena produktu zmieni się później, nie powinna zmieniać znaczenia historycznego `OrderPlaced`. Zdarzenie powinno zawierać taką kwotę i takie pozycje, jakie obowiązywały w chwili złożenia zamówienia.

## Publiczne API systemu

Kontrakty eventów są formą publicznego API. Różnią się od REST API tym, że są asynchroniczne i często konsumowane przez wiele niezależnych komponentów, ale ich znaczenie organizacyjne jest podobne. Zmiana pola, typu albo semantyki może zepsuć konsumentów. Dlatego kontrakty powinny być dokumentowane, testowane i wersjonowane.

W organizacji warto ustalić proces zarządzania schematami. Może to oznaczać użycie schema registry, testów kompatybilności oraz przeglądów zmian kontraktów. Bez takiej dyscypliny architektura event-driven łatwo zamienia się w sieć nieformalnych zależności, w której nikt nie wie, kto używa którego pola i jakie skutki będzie miała jego zmiana.

Stabilność kontraktu nie oznacza braku rozwoju. Oznacza świadome wprowadzanie zmian. Nowe pola powinny być dodawane w sposób bezpieczny dla starych konsumentów. Usuwanie pól powinno odbywać się dopiero po upewnieniu się, że nikt ich nie używa albo po przeprowadzeniu migracji. Zmiana znaczenia istniejącego pola jest szczególnie ryzykowna, ponieważ może nie spowodować błędu technicznego, ale doprowadzić do błędnej interpretacji biznesowej.

## Podsumowanie

Model domenowy i kontrakty eventów są fundamentem systemu event-driven. W dobrze zaprojektowanej architekturze każdy serwis posiada własną odpowiedzialność i własny model danych, a komunikacja między serwisami odbywa się przez stabilne zdarzenia opisujące fakty biznesowe. `Order Service`, `Payment Service` i `Shipping Service` nie powinny współdzielić wewnętrznych encji ani baz danych. Powinny wymieniać się publicznymi kontraktami, takimi jak `OrderPlaced`, `PaymentAuthorized`, `PaymentFailed`, `ShippingInitiated` i `OrderCancelled`.

Zdarzenia powinny mieć metadane pozwalające na identyfikację, wersjonowanie, korelację i analizę przyczynowości. Payload powinien zawierać dane biznesowe potrzebne do sensownego przetworzenia zdarzenia, ale nie powinien być mechaniczną kopią wewnętrznego modelu domenowego. Każde zdarzenie należy traktować jako niemodyfikowalny fakt historyczny i jako publiczne API systemu.

Najważniejsze wyzwania tej architektury nie dotyczą samej technologii brokera, lecz projektowania granic, kontraktów, idempotencji, obserwowalności i ewolucji schematów. Kafka, Avro czy Protobuf są narzędziami. Poprawny model zdarzeń wynika przede wszystkim z dobrego zrozumienia domeny i świadomego zarządzania zależnościami między serwisami.
