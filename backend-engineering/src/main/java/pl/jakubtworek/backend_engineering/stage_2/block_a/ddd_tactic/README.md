# DDD taktyczne – agregaty, encje, Value Objects, repozytoria, serwisy i zdarzenia domenowe

## Wprowadzenie

Taktyczne Domain-Driven Design koncentruje się na implementacji modelu domenowego wewnątrz bounded context. O ile strategiczne DDD definiuje granice systemu i relacje między kontekstami, taktyczne DDD odpowiada za sposób modelowania logiki biznesowej w kodzie. To właśnie tutaj pojawiają się pojęcia:
- encji,
- agregatów,
- Value Objects,
- repozytoriów,
- serwisów domenowych,
- zdarzeń domenowych.

Celem taktycznego DDD nie jest tworzenie skomplikowanych struktur klas, lecz budowanie modelu, który odzwierciedla rzeczywiste reguły biznesowe i pozwala utrzymać kontrolę nad złożonością systemu.

Najważniejszą ideą jest umieszczenie logiki biznesowej w modelu domenowym, a nie w warstwach technicznych czy serwisach aplikacyjnych. Dzięki temu kod staje się bardziej spójny, łatwiejszy do testowania i bliższy językowi biznesu.

---

# Encje

Encja jest obiektem posiadającym tożsamość oraz cykl życia. Encje mogą zmieniać swój stan w czasie, ale nadal pozostają tym samym obiektem domenowym.

Najważniejszą cechą encji jest identyfikator. Dwie encje mogą mieć różne dane, ale jeśli posiadają to samo ID, są traktowane jako ten sam obiekt biznesowy.

Przykładami encji w systemie e-commerce są:
- Order,
- Customer,
- Invoice,
- Shipment,
- Payment.

Encja nie jest jedynie rekordem w bazie danych. Powinna zawierać zachowanie biznesowe oraz pilnować reguł domenowych.

Przykładowo:
- `Order.place()`,
- `Order.cancel()`,
- `Payment.complete()`,
- `Shipment.schedule()`.

To właśnie encja powinna odpowiadać za zmianę swojego stanu.

---

# Anemic Domain Model

Jednym z najczęstszych problemów jest anemiczny model domenowy.

W takim podejściu encje:
- zawierają wyłącznie pola,
- nie posiadają logiki,
- działają jak DTO.

Cała logika biznesowa trafia wtedy do:
- serwisów,
- managerów,
- helperów,
- utili.

Powoduje to:
- utratę enkapsulacji,
- rozproszenie logiki,
- trudniejsze utrzymanie,
- słabą czytelność modelu.

DDD zakłada, że logika biznesowa powinna znajdować się możliwie blisko danych domenowych.

---

# Value Objects

Value Object reprezentuje wartość, a nie byt posiadający własną tożsamość.

Value Object:
- nie posiada ID,
- jest niemutowalny,
- porównywany jest po wartościach,
- nie posiada własnego lifecycle’u.

Przykładami są:
- Money,
- Address,
- Quantity,
- Email,
- Currency,
- Price.

Value Objects znacząco upraszczają model domenowy. Zamiast operować na prymitywach:
- `String`,
- `BigDecimal`,
- `int`,

można modelować pojęcia biznesowe bezpośrednio.

Przykładowo:
- `Money`,
- `ProductId`,
- `CustomerId`.

To zmniejsza ryzyko błędów i poprawia czytelność kodu.

DDD promuje zasadę:
> Jeśli coś nie potrzebuje własnej tożsamości — powinno być Value Object.

---

# Korzyści z Value Objects

Value Objects:
- zmniejszają coupling,
- poprawiają testowalność,
- zwiększają bezpieczeństwo typów,
- upraszczają walidację,
- ograniczają mutowalność.

Przykładowo `Money` może pilnować:
- waluty,
- zaokrągleń,
- operacji matematycznych,
- zakazu ujemnych wartości.

Dzięki temu logika finansowa nie jest rozproszona po systemie.

---

# Agregaty

Agregat jest jedną z najważniejszych koncepcji taktycznego DDD.

Agregat definiuje:
- granicę spójności,
- granicę transakcji,
- granicę invariants biznesowych.

Agregat składa się z:
- Aggregate Root,
- encji wewnętrznych,
- Value Objects.

Wszystkie zmiany wewnątrz agregatu przechodzą wyłącznie przez Aggregate Root.

Przykładowo:
- `Order` jest rootem,
- `OrderLine` istnieje tylko wewnątrz zamówienia.

Nie zapisujemy `OrderLine` osobno przez repozytorium.

---

# Aggregate Root

Aggregate Root kontroluje:
- modyfikacje stanu,
- invariants,
- publikację zdarzeń domenowych.

To root odpowiada za spójność agregatu.

Przykładowo:
- zamówienie nie może zostać złożone bez pozycji,
- zamówienie wysłane nie może zostać anulowane,
- suma pozycji musi zgadzać się z total price.

Root powinien udostępniać zachowania biznesowe, a nie settery.

Zamiast:
```java
order.setStatus(PAID);
```

preferowane jest:
```java
order.markAsPaid();
```

---

# Invariants biznesowe

Invariant to reguła biznesowa, która zawsze musi być spełniona.

Przykłady:
- suma pozycji zamówienia musi zgadzać się z totalem,
- ilość produktu nie może być ujemna,
- wysłane zamówienie nie może zostać anulowane,
- płatność nie może zostać zrealizowana dwa razy.

To agregat odpowiada za ochronę invariantów.

---

# Granica transakcji

Jedna z najważniejszych zasad DDD mówi:
> Jedna transakcja powinna obejmować maksymalnie jeden agregat.

To bardzo ważne w architekturach skalowalnych.

Jeżeli jedna transakcja obejmuje wiele agregatów:
- rośnie coupling,
- pojawiają się konflikty,
- zwiększa się lock contention,
- trudniej skalować system.

DDD zakłada, że koordynacja między agregatami powinna odbywać się przez:
- zdarzenia domenowe,
- eventual consistency,
- mechanizmy asynchroniczne.

---

# Małe agregaty

Jednym z najczęstszych błędów jest tworzenie ogromnych agregatów.

Duży agregat:
- blokuje wiele danych naraz,
- zwiększa konflikty transakcyjne,
- obniża wydajność,
- utrudnia skalowanie.

Nowoczesne DDD preferuje:
- małe agregaty,
- krótkie transakcje,
- komunikację event-driven.

To szczególnie ważne w mikroserwisach i systemach o dużym ruchu.

---

# Referencje między agregatami

Agregaty nie powinny przechowywać bezpośrednich referencji do innych agregatów.

Zamiast:
```java
Customer customer;
```

preferowane jest:
```java
CustomerId customerId;
```

Powody:
- mniejsze sprzężenie,
- łatwiejsze ładowanie danych,
- lepsza skalowalność,
- brak przypadkowych transakcji cross-aggregate.

To jedna z najważniejszych praktyk DDD.

---

# Repozytoria

Repozytorium jest abstrakcją persystencji agregatów.

Repozytorium:
- ładuje agregaty,
- zapisuje agregaty,
- ukrywa szczegóły infrastruktury.

Domena nie powinna znać:
- SQL,
- JPA,
- Hibernate,
- Spring Data,
- ORM.

Dlatego repozytorium w DDD jest portem architektury.

Przykładowo:
```java
orderRepository.save(order);
```

a nie:
```java
entityManager.persist(order);
```

---

# Repozytorium nie jest DAO

Repozytorium nie powinno być:
- wrapperem na SQL,
- warstwą CRUD,
- miejscem raportów,
- miejscem skomplikowanych joinów.

Repozytorium operuje na agregatach i logice domenowej.

Raportowanie oraz projekcje odczytowe powinny być realizowane przez:
- CQRS,
- query models,
- read projections.

---

# Zdarzenia domenowe

Domain Event reprezentuje fakt biznesowy, który już się wydarzył.

Przykłady:
- `OrderPlaced`,
- `OrderPaid`,
- `ShipmentScheduled`,
- `InvoiceIssued`.

Zdarzenia:
- zmniejszają coupling,
- umożliwiają integrację,
- wspierają eventual consistency,
- oddzielają bounded contexts.

---

# Publikacja zdarzeń

Najczęściej agregat:
- zapisuje event lokalnie,
- serwis aplikacyjny publikuje event po zapisaniu agregatu.

To bardzo ważne rozdzielenie odpowiedzialności.

Agregat:
- nie zna Kafki,
- nie zna RabbitMQ,
- nie zna infrastruktury.

Agregat jedynie mówi:
> „To wydarzyło się w domenie.”

---

# Eventual Consistency

Zdarzenia domenowe prowadzą do eventual consistency.

Przykład:
1. Sales publikuje `OrderPlaced`.
2. Billing tworzy płatność.
3. Fulfillment planuje wysyłkę.

Proces nie odbywa się w jednej transakcji.

System osiąga spójność po czasie.

To fundamentalna różnica między monolitem a architekturą event-driven.

---

# Serwisy domenowe

Nie każda logika biznesowa pasuje do jednej encji.

W takich sytuacjach używa się Domain Services.

Domain Service:
- zawiera logikę domenową,
- nie należy do konkretnej encji,
- pozostaje niezależny od infrastruktury.

Przykłady:
- polityka cenowa,
- wyliczanie rabatów,
- ocena ryzyka kredytowego,
- kalkulacja podatków.

Domain Service nie powinien być workiem na logikę.

Jeżeli logika naturalnie należy do agregatu, powinna pozostać w agregacie.

---

# Serwisy aplikacyjne

Application Service:
- orkiestruje przypadek użycia,
- kontroluje transakcję,
- ładuje agregaty,
- zapisuje agregaty,
- publikuje zdarzenia.

Nie powinien zawierać logiki biznesowej.

Przykładowy przepływ:
1. Odbiór komendy.
2. Załadowanie agregatu.
3. Wywołanie metody domenowej.
4. Zapis repozytorium.
5. Publikacja eventów.

To bardzo ważne rozdzielenie:
- domena zawiera reguły,
- application service zawiera orchestration.

---

# CQRS i read models

Repozytoria agregatów nie nadają się do:
- raportów,
- dashboardów,
- analityki,
- skomplikowanych query.

Dlatego stosuje się CQRS.

Write model:
- pilnuje biznesu,
- używa agregatów,
- utrzymuje invariants.

Read model:
- jest zoptymalizowany pod query,
- może być denormalizowany,
- może mieć osobną bazę.

To rozdziela:
- logikę biznesową,
- potrzeby raportowe.

---

# Outbox Pattern

Jednym z największych problemów event-driven architecture jest atomowość:
- zapis danych,
- publikacja eventu.

Może dojść do sytuacji:
- agregat został zapisany,
- event nie został opublikowany.

Outbox Pattern rozwiązuje ten problem.

W tej samej transakcji zapisujemy:
- agregat,
- rekord outbox.

Następnie osobny proces:
- odczytuje outbox,
- publikuje komunikaty,
- oznacza je jako przetworzone.

To standard nowoczesnych architektur DDD.

---

# Punkty bólu modular monolith

Modular monolith rozwiązuje wiele problemów organizacyjnych, ale nie eliminuje wszystkich ograniczeń.

Najczęstsze problemy:
- współdzielona baza danych,
- duże transakcje,
- konflikty deploymentowe,
- zbyt duże agregaty,
- anemiczny model domenowy.

W miarę wzrostu systemu pojawia się pokusa:
- obejmowania wielu agregatów jedną transakcją,
- przenoszenia logiki do application services,
- omijania granic bounded contexts.

To sygnał, że:
- domena została źle zaprojektowana,
- albo system dojrzewa do ekstrakcji mikroserwisów.

---

# Typowe błędy

## Zbyt duże agregaty

Powodują:
- konflikty,
- lock contention,
- słabą skalowalność.

---

## Anemic Domain Model

Logika trafia do serwisów zamiast do domeny.

---

## Repozytoria CRUD

Repozytorium staje się wrapperem nad ORM.

---

## Transakcje obejmujące wiele agregatów

Prowadzą do silnego coupling i problemów skalowania.

---

## Encje bez zachowań

Encje zamieniają się w DTO zamiast reprezentować domenę.

---

# Podejście ewolucyjne

Najlepsze rezultaty daje:
- mały agregat,
- silna enkapsulacja,
- event-driven integration,
- CQRS,
- modularność bounded contexts.

DDD nie polega na liczbie klas, lecz na modelowaniu rzeczywistego biznesu.

Najważniejsze jest utrzymanie:
- spójności modelu,
- jasnych granic,
- kontrolowanej złożoności.

---

# Podsumowanie

Taktyczne DDD dostarcza narzędzi do implementacji modelu biznesowego w sposób spójny i skalowalny. Encje reprezentują byty posiadające tożsamość, Value Objects opisują wartości, agregaty chronią invariants biznesowe, repozytoria abstrahują persystencję, a zdarzenia domenowe umożliwiają luźne powiązanie między kontekstami.

Najważniejszą ideą DDD jest jednak to, że model domenowy powinien być centrum systemu. Frameworki, ORM-y, HTTP czy broker wiadomości są jedynie detalami technologicznymi. To domena definiuje architekturę, a nie odwrotnie.

Dobrze zaprojektowany model:
- redukuje coupling,
- poprawia testowalność,
- upraszcza rozwój,
- umożliwia skalowanie organizacyjne i techniczne.

W praktyce największą wartością DDD nie są same wzorce, lecz zdolność do kontrolowania złożoności biznesowej systemu.