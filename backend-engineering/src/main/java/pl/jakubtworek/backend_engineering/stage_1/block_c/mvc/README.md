# mvc

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** mvc.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „mvc” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=UserApiContractTest,UserServiceContractTest" test`
> - **Role klas:** `PageController` = `production-boundary`, `ReportController` = `production-boundary`, `UserRestController` = `production-boundary`.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## Projektowanie kontraktu HTTP i pipeline Spring MVC



Spring MVC dostarcza mechanikę obsługi requestu, lecz nie zaprojektuje za nas semantyki API. Poprawny kontroler musi jednocześnie rozumieć pipeline frameworka, znaczenie metod i statusów HTTP, retry klienta, współbieżne aktualizacje oraz stabilność publicznego kontraktu.

## Mapa laboratorium

| Element | Zagadnienie |
|---|---|
| `CorrelationIdFilter` | granica servletowa, propagacja identyfikatora i cleanup MDC |
| `RequestLoggingInterceptor` | czas obsługi oraz końcowy status po wykonaniu kontrolera |
| `AuthUserArgumentResolver` | rozszerzenie mechanizmu argument resolution |
| `UserRestController` | statusy, `Location`, ETag, `If-Match` i `Idempotency-Key` |
| `UserService` | atomowa idempotencja oraz optimistic concurrency bez logiki w kontrolerze |
| `MvcExceptionHandler` | jeden format Problem Details dla błędów protokołu i domeny |

## Pipeline requestu

```text
Filter
  -> DispatcherServlet
    -> HandlerMapping
      -> HandlerInterceptor.preHandle
        -> argument resolvers
          -> HttpMessageConverter
            -> Bean Validation
              -> controller
            <- HttpMessageConverter
      <- HandlerInterceptor.afterCompletion
<- Filter
```

Filtr działa przed `DispatcherServlet`, dlatego nadaje się do correlation ID, CORS i elementów bezpieczeństwa na poziomie protokołu. Interceptor zna już wybrany handler i może mierzyć wykonanie MVC. Argument resolver buduje niestandardowy parametr kontrolera. Converter odpowiada za reprezentację, a nie za reguły biznesowe. `@RestControllerAdvice` przechwytuje błędy powstałe w MVC, lecz wyjątki z filtrów bezpieczeństwa mogą wymagać `AuthenticationEntryPoint` lub `AccessDeniedHandler`.

## Semantyka endpointów przykładu

| Operacja | Odpowiedź | Istotne nagłówki |
|---|---|---|
| `POST /api/users` | `201 Created` | request: `Idempotency-Key`; response: `Location`, `ETag` |
| ponowienie tego samego `POST` | ponownie ten sam rezultat | `Idempotency-Replayed: true` |
| ten sam klucz, inny payload | `409 Conflict` | kod `IDEMPOTENCY_KEY_REUSED` |
| `GET /api/users/{id}` | `200 OK` | aktualny `ETag` |
| `PUT /api/users/{id}` | `200 OK` | wymagany `If-Match`; zwracany nowy `ETag` |
| zapis ze starą wersją | `412 Precondition Failed` | kod `STALE_RESOURCE_VERSION` |
| zapis bez warunku | `428 Precondition Required` | kod `IF_MATCH_REQUIRED` |

`POST` tworzący zasób nie powinien zwracać przypadkowego `200`. `Location` wskazuje utworzony zasób. Laboratorium wymaga klucza idempotencji, ponieważ tworzenie użytkownika jest traktowane jak operacja, którą klient może ponowić po utracie odpowiedzi.

## Idempotencja to stan, nie adnotacja

Serwer wiąże `Idempotency-Key` z treścią operacji i jej wynikiem. Powtórzenie tego samego klucza oraz payloadu zwraca zapisany rezultat bez ponownego efektu ubocznego. Ten sam klucz z innym payloadem jest konfliktem — ciche zwrócenie starej odpowiedzi ukrywałoby błąd klienta.

Implementacja in-memory używa atomowego `ConcurrentHashMap.compute`, aby dwa równoległe requesty z tym samym kluczem nie utworzyły dwóch użytkowników. W systemie wieloinstancyjnym potrzebny jest współdzielony, trwały zapis z unikalnym constraintem, statusem `IN_PROGRESS/COMPLETED`, fingerprintem requestu i polityką TTL. Pamięć pojedynczej JVM pokazuje semantykę, ale nie jest produkcyjnym magazynem idempotencji.

## ETag i utracona aktualizacja

ETag `"0"` reprezentuje wersję zasobu. Klient odczytuje zasób, a przy `PUT` wysyła `If-Match: "0"`. Aktualizacja powiedzie się tylko wtedy, gdy bieżąca wersja nadal wynosi zero. Po zapisie serwer zwraca ETag `"1"`. Drugi klient pracujący na starej reprezentacji otrzymuje `412`, zamiast nadpisać cudzą zmianę.

Laboratorium obsługuje pojedynczy, silny, numeryczny ETag. Pełna składnia HTTP dopuszcza listę tagów i `*`; uproszczenie jest jawne. W aplikacji z bazą atomowość powinien zapewnić warunek `UPDATE ... WHERE id = ? AND version = ?` albo `@Version`, a nie sekwencja „odczytaj, potem zapisz”.

## Walidacja na właściwej granicy

- składnia JSON i typy należą do deserializacji;
- rozmiary, format emaila oraz dodatnie ID należą do walidacji DTO/protokołu;
- unikalność i reguły stanu należą do domeny oraz constraintów bazy;
- autoryzacja odpowiada na pytanie, czy dany principal może wykonać przypadek użycia.

Walidacja requestu nie zastępuje invariantów domenowych. Ten sam use case może zostać wywołany przez HTTP, konsumenta wiadomości lub scheduler.

## Stabilność i ewolucja API

Zmiana nazwy pola, znaczenia statusu, opcjonalności lub jednostki jest zmianą kontraktu nawet wtedy, gdy kod Java nadal się kompiluje. Preferuj zmiany addytywne, toleruj nieznane pola po stronie konsumenta i usuwaj stare pole dopiero po zmierzeniu użycia. Wersjonowanie URL nie naprawia złej kompatybilności; jest narzędziem dla rzeczywiście niekompatybilnych semantyk.

Test kontraktowy powinien sprawdzać status, nagłówki, media type, stabilne pola błędu i zachowanie retry. Sam test metody kontrolera nie wykryje problemów z serializacją, walidacją ani advice.

## Granice przykładu

- magazyny użytkowników oraz idempotencji są lokalne dla jednej JVM;
- ETag jest wersją, nie hashem całej reprezentacji;
- endpoint wyszukiwania wykonuje liniowy scan, bo tematem jest HTTP, nie persistence;
- uwierzytelnianie jest przedstawione w osobnym laboratorium `authorization`.
