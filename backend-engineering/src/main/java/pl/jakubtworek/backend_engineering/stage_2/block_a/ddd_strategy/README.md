# DDD strategiczne – bounded contexts i mapa kontekstów w architekturze e-commerce

## Wprowadzenie

Współczesne systemy e-commerce należą do najbardziej złożonych typów aplikacji biznesowych. Nawet stosunkowo prosty sklep internetowy bardzo szybko zaczyna obejmować wiele niezależnych procesów: sprzedaż, obsługę koszyka, płatności, logistykę, fakturowanie, zwroty, integracje z przewoźnikami, systemy magazynowe oraz systemy podatkowe. Wraz ze wzrostem skali organizacji i liczby zespołów problemem przestaje być sama implementacja funkcjonalności, a staje się utrzymanie spójności modelu biznesowego oraz kontrolowanie złożoności systemu.

Domain-Driven Design proponuje rozwiązanie tego problemu poprzez podział systemu na bounded contexts, czyli wyraźnie oddzielone granice modeli domenowych. Bounded context definiuje obszar, w którym obowiązuje konkretny model biznesowy, określone znaczenie pojęć oraz jeden spójny język domenowy. Dzięki temu możliwe jest uniknięcie sytuacji, w której różne części organizacji używają tych samych terminów w różnych znaczeniach, co prowadzi do chaosu architektonicznego i nadmiernego sprzężenia między modułami.

W praktyce bounded contexts stanowią fundament nowoczesnych architektur modularnych i mikroserwisowych. To właśnie od poprawnego wydzielenia kontekstów zależy późniejsza jakość integracji, możliwość niezależnego rozwoju zespołów oraz stabilność całego systemu.

---

# Bounded Context – podstawowa jednostka strategicznego DDD

Bounded Context jest logiczną granicą modelu domenowego. Wewnątrz tej granicy wszystkie pojęcia mają jedno, spójne znaczenie. Poza nią to samo pojęcie może oznaczać coś zupełnie innego.

Najważniejszą cechą bounded context jest to, że posiada własny model biznesowy. Oznacza to:
- własne encje,
- własne agregaty,
- własne reguły biznesowe,
- własne repozytoria,
- własne API,
- własne kontrakty komunikacyjne,
- bardzo często również własną bazę danych.

Granica bounded context nie jest granicą technologiczną, lecz biznesową. Oznacza to, że konteksty powinny być definiowane na podstawie odpowiedzialności domenowych, a nie na podstawie warstw technicznych czy frameworków.

W systemach e-commerce bardzo często występują konteksty takie jak:
- Sales,
- Billing,
- Fulfillment,
- Inventory,
- Customer Management,
- Catalog,
- Pricing,
- Notification.

Każdy z tych kontekstów odpowiada za inny fragment biznesu i posiada własny model domenowy.

---

# Różne znaczenia tych samych pojęć

Jednym z najważniejszych powodów istnienia bounded contexts jest fakt, że te same pojęcia biznesowe mają różne znaczenie w różnych częściach organizacji.

Dobrym przykładem jest pojęcie „Order”.

W kontekście Sales zamówienie oznacza decyzję zakupową klienta. Interesują nas:
- produkty,
- ceny,
- rabaty,
- koszyk,
- status płatności,
- dane klienta.

W kontekście Fulfillment „Order” oznacza proces logistyczny związany z realizacją wysyłki. Najważniejsze stają się:
- adres dostawy,
- kompletacja produktów,
- status paczki,
- przewoźnik,
- tracking.

Natomiast w kontekście Billing zamówienie jest zobowiązaniem finansowym. Interesują nas:
- płatności,
- faktury,
- podatki,
- refundacje,
- status rozliczenia.

Choć wszędzie używane jest słowo „Order”, w praktyce są to trzy różne modele domenowe.

Próba stworzenia jednej wspólnej encji `Order` dla całego systemu zwykle kończy się powstaniem ogromnego, trudnego do utrzymania modelu, który zawiera odpowiedzialności wielu kontekstów jednocześnie.

DDD zakłada, że każdy bounded context powinien posiadać własną reprezentację tych pojęć.

---

# Ubiquitous Language

Każdy bounded context posiada własny ubiquitous language, czyli wspólny język używany zarówno przez programistów, jak i ekspertów domenowych.

Ubiquitous language jest niezwykle ważny, ponieważ pozwala:
- unikać niejednoznaczności,
- modelować biznes w sposób zrozumiały,
- zmniejszać ryzyko błędnych interpretacji,
- utrzymywać spójność komunikacji między zespołami.

W praktyce oznacza to, że nazwy klas, zdarzeń, endpointów i operacji powinny odzwierciedlać rzeczywisty język biznesu.

Przykładowo:
- `OrderPlaced`,
- `ShipmentScheduled`,
- `PaymentCompleted`,
- `InvoiceIssued`.

Nazwy techniczne typu:
- `ProcessOrderTask`,
- `DataManager`,
- `OrderUtils`

nie niosą znaczenia biznesowego i utrudniają zrozumienie domeny.

---

# Konteksty w systemie e-commerce

## Sales Context

Kontekst sprzedażowy odpowiada za proces składania zamówienia przez klienta.

W jego zakresie znajdują się:
- koszyk,
- produkty widoczne dla klienta,
- promocje,
- checkout,
- składanie zamówienia,
- status biznesowy zamówienia.

Sales jest bardzo często centralnym kontekstem systemu e-commerce, ponieważ inicjuje większość procesów biznesowych.

To właśnie Sales publikuje zdarzenie `OrderPlaced`, które uruchamia kolejne procesy w innych kontekstach.

---

## Fulfillment Context

Fulfillment odpowiada za realizację fizycznej wysyłki zamówienia.

Zakres odpowiedzialności obejmuje:
- kompletację produktów,
- magazyn,
- generowanie list pickingowych,
- integracje z przewoźnikami,
- tworzenie przesyłek,
- tracking paczek.

Fulfillment nie musi znać szczegółów koszyka klienta, promocji czy płatności. Interesują go jedynie dane potrzebne do realizacji logistycznej.

---

## Billing Context

Billing odpowiada za procesy finansowe.

Zakres odpowiedzialności obejmuje:
- autoryzację płatności,
- księgowanie,
- faktury,
- refundacje,
- rozliczenia podatkowe.

Billing posiada własny model płatności i nie powinien współdzielić encji z kontekstem sprzedażowym.

---

# Context Map

Context Map opisuje relacje między bounded contexts.

Nie wszystkie konteksty są równorzędne. Niektóre dostarczają dane i reguły biznesowe innym kontekstom, które są od nich zależne.

W architekturze e-commerce Sales często pełni rolę upstream dla Billing i Fulfillment.

Oznacza to, że:
- Sales publikuje zdarzenia,
- Billing i Fulfillment reagują na te zdarzenia,
- downstream nie kontroluje modelu upstream.

Przykładowo:
- `OrderPlaced` trafia do Billing,
- Billing tworzy płatność,
- Fulfillment planuje wysyłkę.

Context Map pozwala zrozumieć:
- kierunek zależności,
- odpowiedzialności,
- kontrakty komunikacyjne,
- przepływy biznesowe.

---

# Typy relacji między kontekstami

## Published Language

Published Language oznacza, że upstream publikuje oficjalny kontrakt komunikacyjny.

Najczęściej:
- zdarzenia domenowe,
- zdarzenia integracyjne,
- publiczne API.

Downstream korzysta wyłącznie z tego kontraktu, bez znajomości wewnętrznego modelu upstream.

Przykładem jest publikacja:
- `OrderPlaced`,
- `PaymentCompleted`,
- `ShipmentScheduled`.

To jedno z najczęściej stosowanych podejść w architekturach event-driven.

---

## Conformist

Relacja conformist oznacza, że downstream dostosowuje się do modelu upstream.

Przykładowo Sales może zaakceptować model płatności narzucony przez Billing albo zewnętrznego operatora płatności.

To podejście jest wygodne, ale zwiększa coupling między kontekstami.

---

## Anti-Corruption Layer

Anti-Corruption Layer (ACL) chroni model domenowy przed wpływem obcych modeli.

Jest to warstwa tłumacząca:
- dane,
- pojęcia,
- protokoły,
- kontrakty.

ACL jest szczególnie ważny podczas:
- migracji legacy systemów,
- integracji z zewnętrznymi systemami,
- migracji monolitu do mikroserwisów.

Bez ACL model domenowy zaczyna przejmować pojęcia z systemów zewnętrznych, co prowadzi do „korupcji” domeny.

Przykładowo:
- stary system może używać pojęcia `ClientOrder`,
- nowy bounded context używa `SalesOrder`,
- ACL tłumaczy jeden model na drugi.

Dzięki temu nowa domena pozostaje czysta i niezależna.

---

# Komunikacja między bounded contexts

Komunikacja między kontekstami powinna odbywać się wyłącznie przez jawne kontrakty.

Najczęściej stosowane mechanizmy:
- REST API,
- gRPC,
- zdarzenia domenowe,
- zdarzenia integracyjne,
- komunikaty asynchroniczne.

Najważniejszą zasadą jest unikanie współdzielenia modeli domenowych między kontekstami.

To oznacza, że:
- nie współdzielimy encji,
- nie współdzielimy agregatów,
- nie współdzielimy tabel bazodanowych,
- nie współdzielimy klas domenowych.

Zamiast tego przekazujemy:
- DTO,
- payloady zdarzeń,
- identyfikatory,
- minimalny zestaw danych.

---

# Zdarzenia domenowe i integracyjne

W systemach event-driven bounded contexts komunikują się przez zdarzenia.

Przykładowe zdarzenie:

```json
{
  "eventType": "OrderPlaced",
  "orderId": "O-123",
  "customerId": "C-789",
  "items": [
    {
      "productId": "P-456",
      "quantity": 2
    }
  ],
  "total": 150.00,
  "timestamp": "2026-02-25T10:15:00Z"
}
```

Takie zdarzenie:
- nie powinno zawierać całego modelu domenowego,
- powinno być stabilnym kontraktem,
- powinno być wersjonowane,
- powinno zawierać wyłącznie potrzebne dane.

Billing może na jego podstawie utworzyć fakturę, a Fulfillment zaplanować wysyłkę.

W drugą stronę Billing może opublikować:
- `PaymentCompleted`,
- `RefundIssued`.

Sales reaguje wtedy zmianą statusu zamówienia.

---

# Własność danych

Jedną z najważniejszych zasad bounded contexts jest własność danych.

Każdy kontekst powinien posiadać:
- własną bazę,
- własny schemat,
- własne repozytoria,
- własne modele.

Współdzielenie jednej tabeli między kontekstami prowadzi do:
- silnego coupling,
- trudnych migracji,
- problemów z deploymentem,
- naruszenia granic domenowych.

Dlatego nowoczesne systemy mikroserwisowe stosują zasadę:
„database per service”.

W modularnym monolicie możliwe jest stosowanie jednej fizycznej bazy, ale z logiczną separacją:
- schematów,
- modułów,
- repozytoriów.

---

# Organizacja zespołów

Bounded context bardzo często odpowiada również granicy organizacyjnej.

Każdy kontekst:
- posiada własny zespół,
- własny lifecycle,
- własny backlog,
- własne decyzje techniczne.

To pozwala zmniejszyć zależności organizacyjne oraz poprawić autonomię zespołów.

Przykładowo:
- SalesTeam rozwija checkout,
- LogisticsTeam rozwija fulfillment,
- FinanceTeam odpowiada za billing.

Takie podejście bardzo dobrze skaluje organizacje.

---

# Typowe błędy

## Wspólny model domenowy

Najczęstszy błąd polega na próbie stworzenia jednego modelu domenowego dla całego systemu.

Prowadzi to do:
- gigantycznych encji,
- nadmiernych zależności,
- konfliktów między zespołami,
- trudności rozwoju.

---

## Mikroserwisy bez bounded contexts

Często organizacje dzielą system na mikroserwisy bez wcześniejszego zdefiniowania bounded contexts.

Efektem jest:
- rozproszony monolit,
- synchroniczne zależności,
- współdzielone bazy danych,
- chaos integracyjny.

---

## Zbyt szczegółowe eventy

Zdarzenia nie powinny przenosić całego modelu domenowego.

Powinny zawierać:
- minimalny potrzebny zestaw danych,
- stabilny kontrakt,
- jawne wersjonowanie.

---

## Brak ACL

Bez warstwy antykorupcyjnej model domenowy zaczyna przejmować pojęcia z legacy systemów lub systemów zewnętrznych.

Powoduje to:
- utratę spójności modelu,
- wzrost coupling,
- trudniejszy rozwój domeny.

---

# Strategia ewolucyjna

Najlepszym podejściem jest budowanie systemu stopniowo.

Najpierw:
- definiujemy bounded contexts,
- budujemy modularny monolit,
- izolujemy modele domenowe.

Dopiero później:
- wprowadzamy komunikację asynchroniczną,
- oddzielamy bazy danych,
- wydzielamy mikroserwisy.

Najważniejsze jest to, że granice domenowe powinny istnieć wcześniej niż granice deploymentowe.

---

# Podsumowanie

Bounded contexts są fundamentem strategicznego Domain-Driven Design. Pozwalają ograniczyć złożoność systemu poprzez podział domeny na autonomiczne obszary odpowiedzialności. Dzięki temu:
- modele pozostają spójne,
- zespoły mogą działać niezależnie,
- komunikacja jest jawna,
- system łatwiej skalować organizacyjnie i technicznie.

W systemach e-commerce bounded contexts pozwalają oddzielić sprzedaż, logistykę i finanse w sposób odpowiadający rzeczywistym procesom biznesowym. To właśnie poprawne granice domenowe decydują o tym, czy architektura będzie elastyczna i rozwijalna, czy stanie się trudnym do utrzymania monolitem rozproszonym po wielu usługach.

DDD nie polega na tworzeniu dużej liczby mikroserwisów, lecz na świadomym modelowaniu biznesu. Mikroserwisy są jedynie techniczną konsekwencją dobrze zaprojektowanych bounded contexts.