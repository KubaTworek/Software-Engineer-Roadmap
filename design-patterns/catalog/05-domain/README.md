# DDD i model domenowy

[Moduł](../../README.md) · [Indeks](../../INDEKS.md) · [Materiały](../../MATERIALY.md#ddd)

Wzorce modelowania i relacji między kontekstami. **15 kart.**

**Status przykładów:** moduł dokumentacyjny. Fragmenty Java, SQL i HTTP oraz pseudokod są objaśnieniami
mechanizmu; kompletne modele i scenariusze poprawności również znajdują się w Markdown. Nie ma osobnej
aplikacji, buildu Java ani testów uruchamianych przez Maven.

**Rozbudowany kod i omówienie:**
[agregat, use case, repozytorium i ACL](../../labs/01-domain-and-persistence.md). Studium w Markdown rozwija
wskazany podzbiór wzorców tej kategorii.

**Jak rozumieć tę kategorię:** DDD porządkuje znaczenie biznesowe, własność reguł i granice spójności. Encja
odpowiada na pytanie „który obiekt”, wartość na „jakie dane”, agregat na „co musi być poprawne razem”.
Konteksty rozdzielają różne znaczenia tych samych słów. Nie wszystkie opisane wzorce wymagają skomplikowanej
domeny: przy prostym CRUD część z nich może nie przynieść korzyści. Przykłady pokazują role i kontrakty, nie
gotowy model całego sklepu.

## Spis kart

- [Entity](#entity)
- [Value Object](#value-object)
- [Aggregate / Aggregate Root](#aggregate--aggregate-root)
- [Domain Service](#domain-service)
- [Application Service / Use Case](#application-service--use-case)
- [Repository](#repository)
- [Specification](#specification)
- [Domain Event](#domain-event)
- [Domain Factory](#domain-factory)
- [Bounded Context](#bounded-context)
- [Anti-corruption Layer / ACL](#anti-corruption-layer--acl)
- [Context Map / Customer–Supplier / Partnership](#context-map--customersupplier--partnership)
- [Open Host Service / Published Language](#open-host-service--published-language)
- [Shared Kernel](#shared-kernel)
- [Conformist / Separate Ways](#conformist--separate-ways)

## Entity

**Intuicja:** Klient zmienia adres i nazwisko, ale nadal pozostaje tym samym klientem. Entity rozdziela zatem
tożsamość od aktualnych atrybutów. To pozwala mówić o ciągłości obiektu w czasie i łączyć z nim historię,
nawet gdy żadne z pól opisowych nie zachowało pierwotnej wartości.

**Problem i mechanizm:** Obiekt jest rozpoznawany przez trwałą tożsamość mimo zmian atrybutów.

**Role i współpraca:** CustomerId identyfikuje encję, Customer przechowuje zmienny stan i zachowanie. Relacje
wskazują ID, a porównanie tożsamości nie powinno zależeć od mutowalnego adresu lub nazwy.

**Przykład:** CustomerId 42 pozostaje tym samym klientem po zmianie adresu; dwa różne identyfikatory nie stają
się równe przez ten sam email.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
final class Customer {
    private final CustomerId id;
    private Address address;

    void relocate(Address newAddress) {
        address = Objects.requireNonNull(newAddress);
    }
    CustomerId id() { return id; }
}
```

**Konkretny przypadek użycia:** Zmiana adresu klienta

- **Wejście:** Klient ID 42 zmienia adres z Krakowa na Gdańsk.
- **Przebieg:** Encja wykonuje relocate, zachowując ID i powiązania historii zamówień.
- **Oczekiwany wynik:** Zmienia się atrybut, nie tożsamość klienta.
- **Błąd lub granica:** Równość oparta na adresie mogłaby popsuć wyszukiwanie obiektu w HashSet po zmianie.

**Kiedy zastosować:** Gdy istotna jest ciągłość i historia obiektu. Jeśli dwie równe wartości są w pełni
wymienne i nie potrzebują własnej historii, Value Object będzie lepszym opisem.

**Koszt i pułapka:** equals/hashCode oparte o mutowalne atrybuty psują kolekcje; nadawanie ID po zapisie
wymaga spójnej strategii.

**Rozwinięcie i ślad wykonania:** Zapisz klienta o ID 42, umieść go w HashSet i zmień email. Element powinien
nadal dać się znaleźć, bo tożsamość nie wynika z email. Jeśli ID jest przydzielane dopiero przez bazę, ustal
zachowanie przed zapisem zamiast zmieniać hashCode obiektu już umieszczonego w kolekcji.

**Ćwiczenie sprawdzające:** Włóż encję do HashSet, zmień adres i sprawdź kontrakt tożsamości.

## Value Object

**Intuicja:** Dwie kwoty 100 PLN znaczą to samo bez względu na miejsce utworzenia obiektów. Value Object
opisuje taki byt bez osobnej tożsamości: liczą się wartości i niezmienniki. Zamiast zmieniać znaczenie
istniejącej kwoty, operacja tworzy nową, co ułatwia porównywanie i bezpieczne współdzielenie.

**Problem i mechanizm:** Brak tożsamości; znaczenie i równość wynikają z wartości oraz niezmienników.

**Role i współpraca:** Money przenosi kwotę i jednostkę, konstruktor chroni dopuszczalną postać, plus oblicza
nową wartość. Równość rekordu odpowiada równości składowych, a nie porównaniu referencji.

**Przykład:** Money(100, PLN).plus(Money(200, PLN)) daje 300 PLN; dodanie EUR jest odrzucane w kodzie
JavaPatterns.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
record Money(long cents, String currency) {
    Money plus(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch");
        }
        return new Money(Math.addExact(cents, other.cents), currency);
    }
}
```

**Konkretny przypadek użycia:** Dodanie cen pozycji

- **Wejście:** Dwie wartości to 100 PLN i 200 PLN; trzecia to 50 EUR.
- **Przebieg:** Money porównuje jednostki i tworzy nową sumę.
- **Oczekiwany wynik:** Dwie pierwsze dają 300 PLN i pozostają niezmienione.
- **Błąd lub granica:** Dodanie EUR bez jawnego kursu jest odrzucone, podobnie jak overflow long.

**Kiedy zastosować:** Dla kwot, zakresów, identyfikatorów i innych pojęć, które mają jednoznaczne znaczenie z
wartości. Nie upychaj w rekordzie dowolnego mutowalnego grafu i nie zakładaj głębokiej niezmienności.

**Koszt i pułapka:** Brak walidacji jednostek tworzy pozornie poprawne obiekty; niezmienność referencji nie
wystarcza dla mutowalnych składowych.

**Rozwinięcie i ślad wykonania:** Money(100, PLN) i drugie Money(100, PLN) są tą samą wartością, choć nie
muszą być tą samą instancją. Operacja plus tworzy nową wartość po sprawdzeniu jednostek i overflow. Dla domeny
zaokrągleń potrzebujesz dodatkowego jawnego kontraktu; long w groszach nie reprezentuje dowolnej precyzji.

**Ćwiczenie sprawdzające:** Przetestuj równość, mieszane waluty i overflow.

## Aggregate / Aggregate Root

**Intuicja:** Reguła zamówienia może wymagać, aby wszystkie pozycje miały jedną walutę i aby potwierdzona suma
nie przekraczała limitu. Jeśli każdy klient dowolnie zmienia osobne pozycje, nikt nie ma pełnego obrazu
podczas decyzji. Aggregate wyznacza granicę, w której jeden właściciel pilnuje wspólnego niezmiennika.

**Problem i mechanizm:** Granica spójności skupia encje i wartości, które muszą zachować niezmiennik w jednej
transakcji.

**Role i współpraca:** Order jest rootem, Lines i Money jego składnikami. Operacje klienta przechodzą przez
root, a repozytorium zapisuje uzgodnioną wersję całości. Inny agregat, np. zapas magazynu, ma własną granicę
decyzji.

**Przykład:** Order.addLine() zmienia pozycje tylko przez root; limit wartości zamówienia sprawdza się przed
commitem.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Order order = orders.load(orderId);
order.addLine(sku, quantity, unitPrice);
order.confirm(spendingLimit);

orders.save(order, expectedVersion);
// Klient nie robi order.lines().get(0).setQuantity(...).
```

**Konkretny przypadek użycia:** Potwierdzenie koszyka

- **Wejście:** Order ma 2 książki po 1500 i 3 długopisy po 200 groszy, limit 4000 PLN.
- **Przebieg:** Root oblicza 3600, sprawdza walutę i przejście DRAFT → CONFIRMED.
- **Oczekiwany wynik:** Jedna decyzja chroni regułę wszystkich pozycji.
- **Błąd lub granica:** Limit 3000 zostawia DRAFT; klient nie może legalnie ominąć root przez setter pozycji.

**Kiedy zastosować:** Gdy reguła naprawdę wymaga spójnej zmiany grupy obiektów w jednej transakcji. Nie
włączaj do agregatu wszystkich powiązanych danych tylko dlatego, że mają wspólne ID w przepływie.

**Koszt i pułapka:** Wielki agregat zwiększa contention; relacje między agregatami zwykle wskazują ID, a nie
graf wspólnej mutacji.

**Rozwinięcie i ślad wykonania:** W OrderWorkflow pozycje są dostępne przez niezmienny snapshot, a
potwierdzenie przechodzi przez root. Root oblicza sumę i sprawdza limit przed zmianą statusu. Test z za niskim
limitem dowodzi, że wyjątek pozostawia DRAFT. W osobnych transakcjach nie da się równie prosto egzekwować tego
samego niezmiennika.

**Ćwiczenie sprawdzające:** Wyjaśnij, dlaczego cały sklep nie powinien być jednym agregatem.

## Domain Service

**Intuicja:** Niektóre reguły należą do domeny, ale nie mają naturalnego właściciela w pojedynczej encji.
Wymiana kwoty według dostarczonego kursu nie musi być metodą klienta ani zamówienia. Domain Service nadaje
takiej regule własną nazwę, zamiast wciskać ją do przypadkowego obiektu lub warstwy HTTP.

**Problem i mechanizm:** Reguła domenowa niemająca naturalnego właściciela w encji lub wartości otrzymuje
nazwany kontrakt.

**Role i współpraca:** CurrencyConversion przyjmuje Money i ExchangeRate, oblicza wynik oraz sprawdza zgodność
pary walut. Aplikacja pozyskuje kurs przez port; serwis domenowy nie musi znać transportu ani menedżera
transakcji.

**Przykład:** CurrencyConversion oblicza wymianę Money z dostarczonym kursem; nie koordynuje HTTP ani
commitów.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
ExchangeRate rate = rates.forDay(from, to, businessDate);
Money converted = conversion.convert(amount, rate, roundingPolicy);
// Pobranie rate jest krokiem aplikacji.
// Zgodność walut i zaokrąglenie należą do reguły domenowej.
```

**Konkretny przypadek użycia:** Przeliczenie oferty

- **Wejście:** Kwota źródłowa ma określoną walutę i dostarczony kurs dnia.
- **Przebieg:** Serwis domenowy sprawdza parę walut i stosuje jawną politykę zaokrąglenia.
- **Oczekiwany wynik:** Wynik jest nową wartością Money bez HTTP wewnątrz obliczenia.
- **Błąd lub granica:** Brak właściwego kursu jest innym wynikiem niż niezgodny kurs do innej pary walut.

**Kiedy zastosować:** Gdy reguła operuje na kilku wartościach lub encjach, a umieszczenie jej w jednej z nich
byłoby sztuczne. Unikaj przenoszenia tu wszystkich zachowań, które mają naturalnego właściciela.

**Koszt i pułapka:** Serwis z każdą regułą prowadzi do anemicznego modelu; application service ma inną
odpowiedzialność.

**Rozwinięcie i ślad wykonania:** Konwersja waluty przyjmuje wartości i znany kurs, więc jej wynik można
obliczyć bez HTTP. Pobranie kursu przez port jest krokiem aplikacyjnym, który dostarcza dane regule. Dzięki
temu test domeny nie musi symulować sieci, a reguła może jawnie odrzucić niewłaściwą datę lub parę walut.

**Ćwiczenie sprawdzające:** Oddziel kalkulację kursu od pobrania kursu z sieci.

## Application Service / Use Case

**Intuicja:** Użytkownik zgłasza zamiar „potwierdź zamówienie”, a system musi odczytać dane, uruchomić reguły
i zapisać wynik. Application Service koordynuje te kroki. Nie zastępuje modelu biznesowego: mówi, kiedy i z
jakimi danymi go użyć, zamiast samemu rozstrzygać wszystkie zasady ceny i legalnych przejść.

**Problem i mechanizm:** Koordynuje przypadek użycia: uprawnienia, odczyt, reguły domeny i zapis w określonej
granicy transakcji.

**Role i współpraca:** ConfirmOrder jest przypadkiem użycia, Orders portem, Order właścicielem reguł. Adapter
wejściowy tworzy komendę, serwis wykonuje orkiestrację, adapter wyjściowy realizuje zapis z wymaganym
kontraktem wersji.

**Przykład:** PlaceOrder wczytuje koszyk, wywołuje reguły Order i zapisuje root oraz outbox; kontroler tylko
tłumaczy HTTP.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Order current = orders.load(command.orderId());
Confirmed fact = current.confirm(command.limit());
transaction.run(() -> {
    orders.save(current, command.expectedVersion());
    outbox.append(fact);
});
```

**Konkretny przypadek użycia:** Use case potwierdzenia

- **Wejście:** Komenda dotyczy order42 w wersji 7.
- **Przebieg:** Serwis ładuje snapshot, wywołuje confirm i zapisuje nową wersję z faktem w Outbox.
- **Oczekiwany wynik:** Fakt staje się widoczny zgodnie z udanym lokalnym commitem.
- **Błąd lub granica:** Konflikt save nie może pozostawić opublikowanego Confirmed dla niezatwierdzonej
  decyzji.

**Kiedy zastosować:** Gdy potrzebujesz czytelnej granicy aplikacji niezależnej od HTTP, kolejki lub UI.
Rozbijaj według zamiaru użytkownika, a nie w jeden serwis zawierający cały system.

**Koszt i pułapka:** Długi serwis z ifami domenowymi ukrywa model. Transakcja bazy nie obejmuje automatycznie
zewnętrznego API.

**Rozwinięcie i ślad wykonania:** ConfirmOrder ładuje snapshot, odtwarza agregat, podejmuje decyzję i zapisuje
wersję o jeden większą. Dopiero po sukcesie save zwraca fakt. Jeśli save odrzuci konflikt, nie wolno
opublikować przygotowanego faktu; trwały mechanizm publikacji powinien współdzielić transakcję ze stanem
zamówienia.

**Ćwiczenie sprawdzające:** Rozdziel PlaceOrder na orkiestrację i niezmienniki agregatu.

## Repository

**Intuicja:** Kod biznesowy potrzebuje znaleźć i zapisać zamówienie, ale nie musi znać nazw tabel i sposobu
łączenia wierszy. Repository przedstawia utrwalone agregaty jak kolekcję z operacjami wynikającymi z domeny.
Tłumaczenie zapytań i zapisów na persystencję pozostaje w implementacji tej granicy.

**Problem i mechanizm:** Udostępnia kolekcję agregatów przez kontrakt domenowy, ukrywając szczegóły odczytu i
zapisu.

**Role i współpraca:** Orders deklaruje wyszukiwanie i zapis rootów. Adapter MemoryOrders służy modelowi
lokalnemu, SqlOrders prawdziwej bazie. Klient nie otrzymuje EntityManager ani możliwości dowolnego
modyfikowania tabel składowych.

**Przykład:** Orders.byId(OrderId) zwraca root, save(order) zapisuje go; kod aplikacji nie otrzymuje
EntityManager.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
interface Orders {
    Optional<Order> byId(OrderId id);
    List<Order> awaitingPaymentBefore(Instant deadline);
    void save(Order order, long expectedVersion);
}
```

**Konkretny przypadek użycia:** Repozytorium zamówień

- **Wejście:** Domena chce znaleźć OrderId 42 i zapisać legalną zmianę.
- **Przebieg:** Port byId zwraca root, adapter składa wiersze i sprawdza wersję podczas save.
- **Oczekiwany wynik:** Use case nie zna tabel order_lines ani EntityManager.
- **Błąd lub granica:** Raport 1000 podsumowań nie musi ładować 1000 całych agregatów; może użyć portu query.

**Kiedy zastosować:** Gdy domena i przypadki użycia mają być niezależne od mapowania danych. Dla raportów użyj
osobnego portu query zamiast udawać, że każde złożone zestawienie jest odczytem jednego agregatu.

**Koszt i pułapka:** Generyczne CRUD dla każdej tabeli ujawnia persystencję i omija granice agregatów.

**Rozwinięcie i ślad wykonania:** Repozytorium zwraca agregat lub jego uzgodniony snapshot, nie otwartą sesję
SQL i dowolny iterator rekordów. Pojedynczy use case może odtworzyć model i zapisać nową wersję. Dla raportu
nie trzeba ładować całych agregatów: osobny port query może zwrócić projekcję zoptymalizowaną do odczytu.

**Ćwiczenie sprawdzające:** Zdefiniuj dwa zapytania wynikające z domeny zamiast udostępniania dowolnego SQL.

## Specification

**Intuicja:** Reguła „może złożyć zamówienie” może występować w kilku przypadkach użycia i składać się z
niezależnych warunków. Specification nadaje jej postać obiektu lub predykatu, który można nazwać i komponować.
Dzięki temu klient pyta o intencję, a nie kopiuje tę samą kombinację ifów.

**Problem i mechanizm:** Reguła kwalifikacji jest obiektem, który można komponować i testować niezależnie.

**Role i współpraca:** Predykaty active i noDebt opisują pojedyncze kryteria, and łączy je w canOrder. Dane
klienta są wejściem, a wynik mówi o kwalifikacji; oczekiwane kontrole nie powinny ukrywać skutków ubocznych.

**Przykład:** CAN_ORDER łączy aktywnego klienta i brak zaległych faktur przez Predicate.and().

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Predicate<Customer> active = Customer::isActive;
Predicate<Customer> noDebt = customer -> customer.unpaidInvoices() == 0;
Predicate<Customer> canOrder = active.and(noDebt);

if (!canOrder.test(customer)) {
    throw new OrderRejected("Customer is not eligible");
}
```

**Konkretny przypadek użycia:** Kwalifikacja klienta

- **Wejście:** Klient A aktywny bez długu, B aktywny z jedną zaległą fakturą.
- **Przebieg:** Specification łączy active i noDebt.
- **Oczekiwany wynik:** A może zamówić, B otrzymuje odmowę z reguły kwalifikacji.
- **Błąd lub granica:** Predicate w pamięci nie dowodzi identycznej semantyki tłumaczenia SQL dla NULL.

**Kiedy zastosować:** Gdy te same reguły wracają w różnych kombinacjach. Jeśli potrzebujesz wyjaśnienia
odmowy, rozważ wynik z listą powodów zamiast samego boolean.

**Koszt i pułapka:** Predicate w pamięci i zapytanie SQL nie mają automatycznie identycznej semantyki null,
kolacji i stref czasu.

**Rozwinięcie i ślad wykonania:** Predicate.and wykonuje drugą regułę tylko, gdy pierwsza zwróci true. Takie
short-circuit jest istotne przy kosztownych kontrolach, ale reguły powinny pozostać bez skutków ubocznych.
Przy przekładaniu na SQL sprawdź semantykę NULL i granice czasu zamiast zakładać identyczność z boolean w
Javie.

**Ćwiczenie sprawdzające:** Porównaj specyfikację na obiekcie i jej odpowiednik w SQL na danych brzegowych.

## Domain Event

**Intuicja:** Po potwierdzeniu zamówienia mogą zareagować wysyłka, raportowanie i powiadomienia. Domena nie
powinna wywoływać na sztywno wszystkich tych odbiorców. Domain Event opisuje fakt, który nastąpił, dzięki
czemu dalsze reakcje można dołączyć poza miejscem podjęcia decyzji i zachować wyraźny moment powstania
informacji.

**Problem i mechanizm:** Opisuje fakt domenowy w czasie przeszłym i umożliwia reakcje bez bezpośredniej
zależności od odbiorcy.

**Role i współpraca:** Order tworzy Confirmed z wartościami z chwili przejścia. Application Service zbiera
fakt i zapisuje go według granicy transakcji. Handler decyduje o reakcji, a publiczny event może mieć osobny
kontrakt integracyjny.

**Przykład:** OrderPlaced zawiera orderId i occurredAt; moduł wysyłki tłumaczy go na własną decyzję.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
record OrderConfirmed(OrderId id, Money total, Instant occurredAt) { }

OrderConfirmed fact = order.confirm(limit, now);
transaction.run(() -> {
    orders.save(order, expectedVersion);
    outbox.append(toIntegrationEvent(fact));
});
```

**Konkretny przypadek użycia:** Reakcje na potwierdzenie

- **Wejście:** Potwierdzenie order42 interesuje wysyłkę i analitykę.
- **Przebieg:** Order tworzy fakt z sumą i czasem, aplikacja utrwala zamiar publikacji.
- **Oczekiwany wynik:** Nowy odbiorca może dołączyć bez dopisywania wywołania do root.
- **Błąd lub granica:** Publikacja przed commitem ujawniłaby fakt, który może zostać wycofany.

**Kiedy zastosować:** Gdy fakt ma znaczenie dla więcej niż jednego odbiorcy lub granicy modelu. Nie zastępuj
eventem zwykłego zwrotu wartości, jeśli żaden niezależny odbiorca go nie potrzebuje.

**Koszt i pułapka:** Zdarzenie wewnętrzne i publiczny integration event mają inne kontrakty; publikacja przed
commitem może ujawnić fakt, który nie zaszedł.

**Rozwinięcie i ślad wykonania:** Confirmed zawiera OrderId oraz obliczony Money, czyli wynik konkretnego
legalnego przejścia. Event wewnętrzny może zostać zmapowany na stabilniejszy publiczny komunikat z eventId i
schemaVersion. Nie publikuj mutowalnej encji: późniejsza zmiana nie może przepisać treści faktu, który już
wysłano.

**Ćwiczenie sprawdzające:** Rozdziel event domenowy od wersjonowanego kontraktu dla partnera.

## Domain Factory

**Intuicja:** Utworzenie agregatu bywa regułą, a nie tylko przypisaniem pól. Trzeba sprawdzić kompletność
pozycji, zgodność walut i wyznaczyć poprawny stan początkowy. Domain Factory skupia te decyzje w jednym
punkcie, aby klient nie mógł zbudować półpoprawnego obiektu przez serię dowolnych setterów.

**Problem i mechanizm:** Tworzenie agregatu z wieloma niezmiennikami trafia do nazwanego miejsca zamiast
rozproszonych setterów.

**Role i współpraca:** Factory otrzymuje potrzebne dane i wydaje poprawny Order lub jawny błąd. Order
zachowuje swoje niezmienniki po utworzeniu. Fabryka nie musi być osobną klasą, jeśli nazwana metoda draft
dobrze oddaje odpowiedzialność.

**Przykład:** OrderFactory.create(customer, lines) wymaga niepustych pozycji i jednej waluty przed wydaniem
obiektu.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Order order = Order.draft(orderId, lines);
// draft sprawdza niepustą listę, waluty i zakres sumy.
orders.insert(order.snapshot(0));
```

**Konkretny przypadek użycia:** Fabryka poprawnego zamówienia

- **Wejście:** Wejście zawiera dwie pozycje PLN albo mieszaną parę PLN/EUR.
- **Przebieg:** Draft waliduje niepustość i total przed wydaniem obiektu.
- **Oczekiwany wynik:** Klient dostaje poprawny obiekt początkowy, a nie serię setterów do uzupełnienia.
- **Błąd lub granica:** Pusta lista i mieszane waluty są odrzucane już podczas tworzenia.

**Kiedy zastosować:** Gdy tworzenie łączy kilka reguł albo ma wiele wariantów znaczeniowych. Prosty walidujący
konstruktor jest dobrym wyborem, jeśli całe tworzenie jest krótkie i jednoznaczne.

**Koszt i pułapka:** Fabryka nie powinna przejmować całej orkiestracji I/O; nie każdy konstruktor wymaga
osobnej klasy.

**Rozwinięcie i ślad wykonania:** Order.draft odrzuca pustą listę i wykonuje total(), dzięki czemu mieszane
waluty i overflow są wykrywane przed wydaniem obiektu. To przenosi odpowiedzialność z klienta na punkt
tworzenia. Odtwarzanie z persystencji również powinno zachować niezmienniki, zamiast ufać historycznym danym
bez granic.

**Ćwiczenie sprawdzające:** Pokaż, że nie można otrzymać częściowo poprawnego Order.

## Bounded Context

**Intuicja:** Słowo „produkt” znaczy co innego w katalogu, magazynie i księgowości. Próba utworzenia jednej
wspólnej encji zbiera wszystkie te znaczenia i sprzęga zmiany zespołów. Bounded Context wyznacza obszar, w
którym określony model i język są spójne, a przejście do innego obszaru wymaga jawnego tłumaczenia.

**Problem i mechanizm:** Model i język obowiązują we wskazanej granicy organizacyjnej i logicznej.

**Role i współpraca:** CatalogProduct opisuje ofertę, StockItem dostępne sztuki. Kontrakty integracji
przenoszą uzgodnione identyfikatory oraz fakty, ale nie narzucają drugiej stronie całego prywatnego modelu.

**Przykład:** Product w katalogu opisuje ofertę, a SKU w magazynie zapas; integracja wymaga jawnego mapowania.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
record CatalogProduct(String sku, String title, Money listPrice) { }
record StockItem(String sku, int availableUnits) { }

// Zgodny sku łączy fakty; nie scala obu modeli w jedną encję.
inventory.reserve(product.sku(), requestedUnits);
```

**Konkretny przypadek użycia:** Katalog i magazyn

- **Wejście:** Ten sam SKU ma opis marketingowy i stan 12 sztuk.
- **Przebieg:** Catalog modeluje ofertę, Inventory zapas; kontrakt wymienia tylko potrzebne fakty.
- **Oczekiwany wynik:** Zmiana opisu nie zmienia reguł rezerwacji sztuk.
- **Błąd lub granica:** Jedna globalna encja Product sprzęgłaby oba modele z każdym nowym polem.

**Kiedy zastosować:** Gdy pojęcia mają różne reguły lub właścicieli. Kontekst nie jest automatycznie
mikroserwisem; można go utrzymać jako moduł jednego wdrożenia.

**Koszt i pułapka:** Jeden globalny model wiąże zespoły; osobny kontekst nie oznacza automatycznie osobnego
mikroserwisu.

**Rozwinięcie i ślad wykonania:** Słowo Product ma znaczenie tylko w modelu konkretnego kontekstu. Nie zmuszaj
magazynu do przyjmowania wszystkich marketingowych pól katalogu. Zidentyfikuj właściciela każdego faktu, a
następnie kontrakt tłumaczenia; dwa konteksty mogą nadal działać w jednym procesie, jeśli pomaga to wdrożeniu
i spójności.

**Ćwiczenie sprawdzające:** Opisz dwa znaczenia Customer i kontrakt między nimi.

## Anti-corruption Layer / ACL

**Intuicja:** Obcy system może używać innych statusów, jednostek i znaczeń niż twoja domena. Bez ochronnej
granicy jego pojęcia zaczną pojawiać się we wszystkich regułach. Anti-corruption Layer tłumaczy tę semantykę
na twój model i zatrzymuje nieobsługiwane przypadki w jednym, rozpoznawalnym miejscu.

**Problem i mechanizm:** Tłumaczy semantykę obcego modelu na własny, chroniąc domenę przed jego pojęciami.

**Role i współpraca:** Adapter ERP zna liczbowy kod, status domenowy zna tylko uzgodnione znaczenie.
Translator jawnie mapuje wartości i błędy. Wewnątrz Order nie powinny pojawiać się warunki typu erpStatus ==
20.

**Przykład:** Adapter ERP mapuje status 7 na domenowe Reserved, odrzuca nieznane kody i tłumaczy jednostki
ilości.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Status translate(int erpStatus) {
    return switch (erpStatus) {
        case 10 -> Status.DRAFT;
        case 20 -> Status.CONFIRMED;
        default -> throw new UnsupportedErpStatus(erpStatus);
    };
}
```

**Konkretny przypadek użycia:** Status z ERP

- **Wejście:** ERP zwraca 10 dla DRAFT, 20 dla CONFIRMED, później dodaje 30.
- **Przebieg:** ACL mapuje znane kody i izoluje słownik dostawcy.
- **Oczekiwany wynik:** Order używa własnego Status zamiast warunków na liczbach ERP.
- **Błąd lub granica:** Kod 30 trafia do kontrolowanego błędu integracji, nie do domyślnego CONFIRMED.

**Kiedy zastosować:** Przy integracji z legacy albo obcym modelem, którego zmian nie kontrolujesz. Samo
przekopiowanie JSON do rekordu nie jest ACL, jeśli nadal przenosisz niezgodną semantykę do rdzenia.

**Koszt i pułapka:** Więcej mapowania i polityk błędów; zwykła zmiana JSON na DTO może nie chronić semantyki.

**Rozwinięcie i ślad wykonania:** fromLegacyStatus przyjmuje znane kody 10 i 20, ale dla 30 rzuca błąd zamiast
mapować domyślnie na CONFIRMED. Ta decyzja chroni domenę przy zmianie ERP. Naprawa integracji trafia do
adaptera i jego testów, bez rozpraszania kodów dostawcy po agregatach oraz kontrolerach.

**Ćwiczenie sprawdzające:** Wprowadź nowy status ERP i sprawdź kontrolowane odrzucenie zamiast błędnego
defaultu.

## Context Map / Customer–Supplier / Partnership

**Intuicja:** Sama wiedza, że dwa moduły się komunikują, nie mówi, kto decyduje o kontrakcie i jak wygląda
współpraca przy zmianie. Context Map pokazuje relacje między modelami oraz zespołami. Customer–Supplier
opisuje uwzględnianie potrzeb downstreamu, Partnership wspólną odpowiedzialność za uzgadnianie rozwiązania.

**Problem i mechanizm:** Mapa nazywa kierunek zależności między modelami i sposób uzgadniania kontraktu.

**Role i współpraca:** Upstream dostarcza dane lub kontrakt, downstream na nim polega. Mapa nazywa kierunek
wpływu, sposób tłumaczenia i uzgodnienia. Role są relacyjne: jeden kontekst może być upstreamem dla jednego
partnera i downstreamem dla innego.

**Przykład:** Zespół Fulfillment jest downstreamem Catalog i negocjuje pola potrzebne do pakowania jako
customer upstreamowego supplier.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
Catalog (upstream)
    -- Published Language: ProductPublished -->
Fulfillment (downstream, uzgodnione potrzeby customer)

Fulfillment <---- Partnership ----> Delivery Planning
```

**Konkretny przypadek użycia:** Uzgodnienie kontraktu magazynu

- **Wejście:** Fulfillment potrzebuje maksymalnej masy przesyłki od Catalog.
- **Przebieg:** Mapa nazywa Catalog upstreamem, wskazuje potrzeby downstreamu i sposób uzgadniania zmiany.
- **Oczekiwany wynik:** Wiadomo, kto utrzymuje pole i kto zależy od jego znaczenia.
- **Błąd lub granica:** Strzałka HTTP bez relacji odpowiedzialności nie rozwiązuje konfliktu priorytetów
  zespołów.

**Kiedy zastosować:** Gdy zmiany przekraczają granice modeli i zespołów. Użyj mapy do rozmowy o
odpowiedzialności; nie próbuj odtworzyć relacji organizacyjnych wyłącznie ze struktury wywołań HTTP.

**Koszt i pułapka:** Podobny transport nie oznacza jednakowego modelu współpracy; mapa musi odzwierciedlać
rzeczywisty wpływ zespołów.

**Rozwinięcie i ślad wykonania:** Mapa powinna pokazać nie tylko strzałkę danych, lecz również wpływ na zmianę
kontraktu. Customer–Supplier daje downstreamowi uzgodnione potrzeby, Partnership zakłada wspólną koordynację.
Identyczny JSON może funkcjonować w zupełnie innej relacji organizacyjnej, więc nazwy nie wywnioskujesz
wyłącznie z transportu.

**Ćwiczenie sprawdzające:** Narysuj relację partnerstwa oraz relację z jednostronnym wpływem na kontrakt.

## Open Host Service / Published Language

**Intuicja:** Gdy wielu odbiorców potrzebuje tych samych danych, osobny prywatny format dla każdego szybko
komplikuje utrzymanie. Open Host Service zapewnia wspólne, opisane wejście, a Published Language stabilny
język wymiany. To dwa powiązane elementy: protokół dostępu i znaczenie danych, które nim przepływają.

**Problem i mechanizm:** Upstream udostępnia stabilny protokół i opisany język wymiany wielu odbiorcom.

**Role i współpraca:** Host publikuje kontrakt, konsumenci implementują jego obsługę, schemat i dokumentacja
wyjaśniają statusy, jednostki oraz kompatybilność. Wewnętrzny model bazy może zmieniać się niezależnie od tego
języka.

**Przykład:** Catalog publikuje wersjonowane API SKU i schemat ProductPublished zamiast indywidualnego modelu
dla każdego klienta.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
record ProductPublishedV1(
        String eventId,
        String sku,
        long priceInMinorUnits,
        String currency) { }
// Kontrakt wyjaśnia znaczenie jednostek, nie tylko typ long.
```

**Konkretny przypadek użycia:** Publiczny event oferty

- **Wejście:** Trzech partnerów czyta ProductPublishedV1 z ceną w jednostkach minimalnych.
- **Przebieg:** Host publikuje jeden opisany schemat, adapter mapuje prywatny model na kontrakt.
- **Oczekiwany wynik:** Partnerzy nie zależą od nazwy kolumny wewnętrznej bazy.
- **Błąd lub granica:** Cicha zmiana jednostki ceny łamie język wymiany nawet przy tym samym typie long.

**Kiedy zastosować:** Gdy wielu odbiorców korzysta z podobnych możliwości i warto utrzymywać jeden stabilny
kontrakt. Nie publikuj prywatnych tabel jako modelu integracyjnego bez reguł jego ewolucji.

**Koszt i pułapka:** Stabilny kontrakt kosztuje kompatybilność, dokumentację i okres wycofywania wersji.

**Rozwinięcie i ślad wykonania:** Opublikowany język określa jednostki, statusy, kompatybilność i znaczenie
pól, a nie tylko schemat JSON. Upstream udostępnia jeden spójny model integracyjny zamiast prywatnej
reprezentacji tabel. Dodanie pola wymaga oceny starszych odbiorców, a zmiana znaczenia istniejącego pola może
wymagać nowej wersji.

**Ćwiczenie sprawdzające:** Dodaj opcjonalne pole bez psucia starszego konsumenta.

## Shared Kernel

**Intuicja:** Dwa konteksty mogą naprawdę potrzebować małego wspólnego fragmentu modelu. Shared Kernel
utrzymuje ten fragment razem, ale zobowiązuje obie strony do uzgadniania zmian. Nie jest to skrót do jednej
wielkiej biblioteki domenowej; im większy wspólny obszar, tym mniej niezależne stają się oba modele.

**Problem i mechanizm:** Mały uzgodniony fragment modelu jest współdzielony przez konteksty wraz z regułami
jego wspólnej zmiany.

**Role i współpraca:** Wspólne typy i testy mają uzgodnionych właścicieli. Każdy kontekst zachowuje prywatne
reguły wokół nich. Zmiana Currency wymaga wspólnego review, zmiana marketingowego opisu produktu nie powinna.

**Przykład:** Dwa moduły współdzielą definicję Currency i testy, ale nie wspólną encję Customer z wszystkimi
polami.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
// Mały uzgodniony kontrakt shared kernel:
record CurrencyCode(String value) { }

// Prywatne modele kontekstów nadal pozostają osobne:
record Offer(String sku, CurrencyCode currency) { }
record Settlement(String id, CurrencyCode currency) { }
```

**Konkretny przypadek użycia:** Wspólna waluta dwóch modułów

- **Wejście:** Billing i Settlement współdzielą CurrencyCode, ale inne encje.
- **Przebieg:** Mały kernel ma wspólne testy oraz uzgadniany proces zmiany.
- **Oczekiwany wynik:** Oba moduły rozumieją identyczny kod waluty bez wspólnego Customer.
- **Błąd lub granica:** Dodanie prywatnych reguł jednego modułu do kernela zwiększa koszt koordynacji obu.

**Kiedy zastosować:** Gdy semantyka jest faktycznie wspólna i zespoły akceptują koordynację. Przypadkowo
podobny rekord nie uzasadnia wspólnego modułu, jeśli modele mają różne cele i tempo zmian.

**Koszt i pułapka:** Każda zmiana wymaga koordynacji; szeroka biblioteka domenowa blokuje niezależne
wdrożenia.

**Rozwinięcie i ślad wykonania:** Wspólny Currency można zmieniać z uzgodnionymi właścicielami i wspólnymi
testami. Współdzielenie całego Customer zaczyna przenosić prywatne reguły obu kontekstów do jednej biblioteki.
Przed dodaniem pola oceń, czy naprawdę jest wspólnym pojęciem, czy tylko chwilowo podobną reprezentacją dwóch
różnych modeli.

**Ćwiczenie sprawdzające:** Wyznacz minimalny wspólny model i właścicieli jego zmian.

## Conformist / Separate Ways

**Intuicja:** Nie zawsze opłaca się chronić własny model rozbudowaną warstwą tłumaczenia. Conformist świadomie
przyjmuje model silniejszego dostawcy, gdy downstream ma mały wpływ i koszt niezależności byłby za duży.
Separate Ways to odrębna decyzja: nie integrować obszarów, gdy wartość wymiany danych nie uzasadnia
sprzężenia.

**Problem i mechanizm:** Downstream przyjmuje model upstreamu przy braku wpływu; Separate Ways wybiera brak
integracji, gdy koszt przewyższa korzyść.

**Role i współpraca:** W Conformist downstream zależy bezpośrednio od opublikowanego języka upstreamu. W
Separate Ways każda strona utrzymuje swój model bez kanału synchronizacji. Wybór powinien mieć widoczne
konsekwencje dla zmian i danych.

**Przykład:** Mały raport używa kodów zewnętrznego dostawcy bez własnego słownika; niezależne narzędzie może w
ogóle nie synchronizować danych.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
Conformist:
    raport przyjmuje statusy dostawcy i aktualizuje się razem z nimi

Separate Ways:
    narzędzie A i narzędzie B nie synchronizują tych modeli
```

**Konkretny przypadek użycia:** Niewielki importer partnera

- **Wejście:** Raport jest całkowicie zależny od stabilnego modelu dostawcy.
- **Przebieg:** Wariant Conformist przyjmuje jego pojęcia; osobne narzędzie bez potrzeby danych wybiera
  Separate Ways.
- **Oczekiwany wynik:** Decyzja jest proporcjonalna do wartości integracji.
- **Błąd lub granica:** Kluczowa niezależna domena może potrzebować ACL zamiast akceptacji obcego modelu.

**Kiedy zastosować:** Dla prostych zależnych narzędzi albo obszarów o niskiej wartości integracji. Jeśli
pojęcia dostawcy podważają kluczowe reguły twojej domeny, ACL może być wart dodatkowego kosztu.

**Koszt i pułapka:** Conformist przenosi zmiany dostawcy do własnego kodu. Separate Ways oznacza świadomie
utraconą wymianę danych.

**Rozwinięcie i ślad wykonania:** Conformist przyjmuje ograniczenia upstreamu świadomie, zamiast deklarować
własny model i kopiować obce pojęcia wszędzie. Separate Ways oznacza inną decyzję: integracja nie powstaje.
Porównaj koszt tłumaczenia, utrzymania i błędów z wartością wymiany danych; brak integracji może być lepszy
niż pozornie uniwersalny model.

**Ćwiczenie sprawdzające:** Porównaj ACL, Conformist i brak integracji dla małego importera.
