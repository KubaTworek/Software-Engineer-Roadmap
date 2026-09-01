# api design

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `praktyka-produkcyjna`
> - **Uczy:** api design.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „api design” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=ApiContractCompatibilityTest,AsyncCancellationTest,OpenApiContractTest" test`
> - **Role klas:** `OrderApiController` = `production-boundary`.
> - **Granica:** Laboratorium pokazuje kontrakt i failure modes; nie zastępuje pełnego testu end-to-end ani operacyjnej konfiguracji środowiska.
<!-- material-card:end -->

## Projektowanie i ewolucja API — wykonywalne laboratorium



To laboratorium łączy mechanikę Spring MVC z granicą aplikacyjną i kontraktem
publicznym. Jego celem nie jest pokazanie wszystkich adnotacji frameworka, lecz
odpowiedź na pytanie: **jakie zachowanie klient może uznać za gwarancję API?**

## Przepływ

```text
HTTP request
  -> walidacja składni i kształtu
  -> mapowanie DTO na komendę
  -> reguły domenowe i kontrola wersji
  -> reprezentacja zasobu albo Problem Details
  -> OpenAPI i test kompatybilności
```

Kod domenowy nie zna statusów HTTP. Adapter tłumaczy wynik use case'u na
protokół, a [kontrakt OpenAPI](../../../../../../../resources/openapi/order-api-v1.yaml)
opisuje to, co otrzymuje klient.

## Mapa kodu

| Element | Odpowiedzialność |
| --- | --- |
| `OrderApiController` | metody HTTP, statusy, nagłówki, DTO i URI zasobów |
| `OrderService` | atomowa idempotencja, reguły zamówienia, wersja i keyset pagination |
| `ApiProblemHandler` | jednolity kontrakt błędów `application/problem+json` |
| `AsyncCancellationService` | rozdzielenie przyjęcia komendy od zakończenia pracy |
| `WebhookDeliveryService` | at-least-once delivery, stały delivery id i ograniczone retry |
| `HmacWebhookSigner` | podpis HMAC nad timestampem i surowym payloadem |
| `ApiContractCompatibilityChecker` | mały model breaking-change gate dla CI |

Kontroler ma profil `api-design-lab`, aby edukacyjny adapter składany ręcznie w
testach nie został przypadkiem częścią głównej aplikacji kompendium. Uruchomienie
go jako endpointu wymaga aktywowania profilu i dostarczenia beanów portów;
MockMvc tworzy dokładnie taki jawny skład bez uruchamiania całego kontekstu.

## Semantyka metod

| Metoda | Znaczenie w laboratorium | Sukces | Ważna gwarancja |
| --- | --- | --- | --- |
| `POST /orders` | utworzenie zasobu | `201 Created` | ten sam klucz i payload zwraca ten sam rezultat |
| `GET /orders/{id}` | reprezentacja zasobu | `200 OK` | `ETag` identyfikuje wersję; `If-None-Match` może dać `304` |
| `PUT /orders/{id}` | pełne zastąpienie pól kontrolowanych przez klienta | `200 OK` | wymaga aktualnego `If-Match` |
| `PATCH /orders/{id}` | częściowa zmiana pól | `200 OK` | używa `application/merge-patch+json` i `If-Match` |
| `DELETE /orders/{id}` | usunięcie aktualnej wersji | `204 No Content` | stary klient nie usunie nowszej reprezentacji |
| `POST /orders/{id}/cancellations` | uruchomienie pracy | `202 Accepted` | odpowiedź wskazuje osobny zasób operacji |

`PUT` nie służy do „większego patcha”. Klient przesyła kompletny zestaw pól,
które wolno mu kontrolować. Nie przesyła `id`, `status`, `version` ani
`createdAt`, ponieważ są własnością serwera. `PATCH` zachowuje pola pominięte.
W uproszczonym modelu `null` również oznacza pominięcie — API nie posiada pola,
którego ustawienie na `null` byłoby operacją domenową. Gdyby takie pole powstało,
DTO musiałoby rozróżniać „brak właściwości” od jawnego JSON `null`.

## Idempotency key

`Idempotency-Key` identyfikuje **logiczną komendę**, nie użytkownika ani zasób.
Pierwsze żądanie atomowo zapisuje fingerprint wejścia i odpowiedź. Kolejne:

- z tym samym kluczem i wejściem dostaje ten sam zasób oraz
  `Idempotency-Replayed: true`;
- z tym samym kluczem i innym wejściem dostaje `409 Conflict`;
- z nowym kluczem tworzy nową komendę.

Wersja in-memory celowo pokazuje niezmiennik. Produkcyjny zapis musi być trwały,
posiadać unikalny constraint na `(scope, idempotency_key)`, określoną retencję
oraz przechowywać status, nagłówki i body odpowiedzi. Fingerprint powinien
powstawać z kanonicznej reprezentacji, aby kolejność właściwości JSON nie
zmieniała znaczenia komendy. Nie wolno zapisać efektu biznesowego i rekordu
idempotencji w dwóch niezależnych transakcjach.

## ETag i ochrona przed lost update

Każda zmiana zwiększa `version`, z której powstaje silny ETag, np. `"v3"`.
Mutacja bez `If-Match` zwraca `428 Precondition Required`, a mutacja ze starą
wersją `412 Precondition Failed`.

```text
klient A: GET -> "v1"
klient B: GET -> "v1"
klient A: PUT If-Match "v1" -> "v2"
klient B: PATCH If-Match "v1" -> 412, zamiast nadpisać zmianę A
```

Sprawdzenie wersji i zapis muszą być jedną operacją, np. `UPDATE ... WHERE id=?
AND version=?`. Samo porównanie w aplikacji przed bezwarunkowym zapisem nadal
ma race condition.

## Problem Details i klasy błędów

Każdy kontrolowany błąd ma media type `application/problem+json`, URI typu
problemu i stabilne pole `code`:

| Status | Znaczenie |
| ---: | --- |
| `400` | JSON jest błędny, brakuje elementu protokołu albo bean validation odrzuca dane |
| `404` | wskazany zasób nie istnieje |
| `409` | konflikt użycia klucza idempotencji |
| `412` | klient operuje na nieaktualnej wersji |
| `422` | poprawne składniowo dane naruszają niezmiennik domeny |
| `428` | bez precondition serwer nie wykona ryzykownej mutacji |

Tekst `detail` jest dla człowieka i może się zmienić. Klient automatyczny reaguje
na status oraz `code`. Walidacja wejścia zawiera listę pól, ale błąd domenowy nie
udaje błędu składniowego tylko dlatego, że został wykryty w tym samym requestcie.

## Paginacja, filtrowanie i sortowanie

Lista obsługuje:

- limit od 1 do 100,
- filtr po statusie,
- `createdAt` oraz `-createdAt`,
- nieprzezroczysty cursor oparty o `(createdAt, id)`.

Id jest tie-breakerem, więc rekordy z tym samym timestampem mają pełny porządek.
Cursor przenosi ostatni klucz sortowania, a nie numer strony. Dzięki temu koszt
nie rośnie jak przy dużym `OFFSET`, a wstawienie wcześniejszego rekordu nie
przesuwa już przeczytanych stron. Produkcyjny cursor warto podpisać, wersjonować
i ograniczyć jego ważność; odpowiadający query wymaga indeksu zgodnego z filtrem
i kolejnością.

## `202 Accepted` i zasób operacji

Przyjęcie anulowania nie oznacza zakończenia anulowania. API zwraca:

- `202 Accepted`,
- `Location: /api/v1/operations/{id}`,
- stan `PENDING`.

Worker zmienia stan operacji na `SUCCEEDED` dopiero po wykonaniu efektu. Klient
może odpytać zasób operacji. W pełnym systemie operacja potrzebuje także stanów
`FAILED`, informacji o bezpiecznym retry, retencji i autoryzacji zgodnej z
zasobem źródłowym.

## Webhook: podpis i redelivery

Webhook ma semantykę at-least-once. Każde ponowienie zachowuje:

- `Webhook-Delivery-Id` używany przez odbiorcę do deduplikacji,
- dokładnie te same bajty payloadu,
- timestamp podpisu,
- podpis `HMAC-SHA256(timestamp + "." + payload)`.

Nie należy generować nowego delivery id przy retry, bo odbiorca nie rozpozna
duplikatu. Próby mają ograniczony budżet i exponential backoff. Produkcyjny
odbiorca dodatkowo porównuje podpis constant-time, odrzuca zbyt stary timestamp
i zapisuje delivery id atomowo z efektem. Nadawca potrzebuje rotacji sekretu,
DLQ oraz ręcznego redrive. Webhook powinien powstać z trwałego Outboxa — callback
wykonany bezpośrednio w transakcji requestu może zostać utracony.

## OpenAPI i kompatybilność

OpenAPI jest wersjonowanym artefaktem wejściowym dla dokumentacji, klientów i
testów. `OpenApiContractTest` pilnuje obecności operacji, preconditions, statusów,
Problem Details oraz webhooka. `ApiContractCompatibilityChecker` pokazuje
minimalną politykę CI:

- usunięcie operacji jest breaking change,
- nowe wymagane pole requestu jest breaking change,
- usunięcie pola response albo obsługiwanego statusu sukcesu jest breaking change,
- nowe opcjonalne pole lub nowa operacja mogą być kompatybilne wstecz.

W produkcji ten mały checker należy zastąpić narzędziem parsującym pełne OpenAPI
i porównującym parametry, typy, enumy, formaty i security schemes. Sam numer `/v2`
nie jest strategią ewolucji; najpierw preferuj zmianę addytywną, okres deprecacji
i pomiar użycia starego kontraktu.

## Testy

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=OrderApiHttpSemanticsTest,OrderPaginationTest,AsyncCancellationTest,WebhookDeliveryTest,ApiContractCompatibilityTest,OpenApiContractTest" test
```

Testy sprawdzają kontrakt przez MockMvc, a nie tylko bezpośrednie wywołanie
metody kontrolera. Osobne testy pokazują stabilność cursora, rozdzielenie
`PENDING` od efektu biznesowego, zachowanie webhooka przy redelivery oraz
odrzucenie breaking changes.

## Granice laboratorium

- repozytorium i idempotency store są in-memory — przykład pokazuje atomowość,
  ale nie trwałość ani współdzielenie między instancjami;
- operacja asynchroniczna jest kończona jawnie w teście, bez brokera i workera;
- OpenAPI ma test powierzchni kontraktu, nie pełny parser specyfikacji;
- webhook nie wykonuje prawdziwego HTTP i nie modeluje per-endpoint bulkheadu;
- uwierzytelnianie i ownership zasobu są omówione w
  [Stage 1C](../../../stage_1/block_c/authorization/README.md), aby laboratorium
  nie mieszało semantyki API z konfiguracją Resource Servera.

## Powiązane materiały

- [Spring MVC](../../../stage_1/block_c/mvc/README.md) — resolvery, filtry,
  konwertery, idempotencja i ETag na poziomie frameworka;
- [ewolucja kontraktu eventów](../../block_b/versioning/README.md) — kompatybilność
  published language w komunikacji asynchronicznej;
- [granice use case'u](../use_case/README.md) — adapter HTTP → aplikacja → domena;
- [progressive delivery](../../block_c/progressive_delivery/README.md) — canary,
  rollback i zgodność kontraktu podczas wdrożenia.
