# aspect

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** aspect.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „aspect” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=PaymentServiceAopTest" test`
> - **Role klas:** brak klasy-kontrprzykładu; pozostałe typy są minimalnymi modelami pojęć opisanych niżej.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## AOP i mechanizm proxy w Spring



## Po co istnieje AOP

Aspect-Oriented Programming wydziela zachowania przekrojowe, które dotyczą wielu przypadków użycia, ale nie stanowią ich logiki biznesowej. Typowe przykłady to transakcje, autoryzacja metod, cache, pomiar czasu, logowanie techniczne i kontrolowane retry. Spring AOP realizuje je przede wszystkim przez proxy otaczające bean.

Najważniejszy model wywołania wygląda tak:

```text
caller -> proxy Springa -> advice/aspect -> metoda obiektu docelowego
```

Jeśli wywołanie nie przejdzie przez proxy, advice się nie wykona. To wyjaśnia większość pozornie „losowych” problemów z `@Transactional`, `@Cacheable` i `@PreAuthorize`.

## Mapa kodu

| Klasa | Pokazywane zagadnienie |
| --- | --- |
| `LoggingAspect` | advice wokół wywołania i obserwacja wyniku |
| `PerformanceAspect` | pomiar czasu wykonania |
| `SecurityAspect` | przekrojowa kontrola przed operacją |
| `RetryAspect` / `RetryableOperation` | retry sterowane adnotacją i klasyfikacją wywołania |
| `ProductCacheService` | cache jako zachowanie proxy |
| `ProxyAwareService` | granica self-invocation |
| `AopConfig` / `AopDemoRunner` | konfiguracja oraz uruchomienie przykładów |

## Advice i pointcut

Pointcut wybiera metody objęte aspektem. Advice określa, kiedy i jak wykona się zachowanie:

- `@Before` — przed metodą,
- `@After` — po zakończeniu niezależnie od wyniku,
- `@AfterReturning` — po sukcesie,
- `@AfterThrowing` — po wyjątku,
- `@Around` — pełna kontrola przed i po wywołaniu.

`@Around` musi wywołać `ProceedingJoinPoint.proceed()`, jeśli metoda docelowa ma się wykonać. Może też zmienić argumenty, wynik lub obsługę wyjątku, dlatego powinien być mały i przewidywalny. Kolejność wielu aspektów należy ustalać jawnie przez `@Order`/`Ordered`, szczególnie gdy łączą się bezpieczeństwo, transakcja, retry i logowanie.

## JDK proxy, CGLIB i self-invocation

JDK dynamic proxy działa przez interfejs, a proxy klasowe tworzy podklasę. Metody `final`, prywatne i obiekty utworzone poza kontenerem nie są dobrym celem dla Spring AOP. Dokładny wybór rodzaju proxy zależy od konfiguracji i wersji Springa, więc nie warto opierać projektu na założeniu „interfejs zawsze oznacza JDK proxy”.

Self-invocation oznacza wywołanie `this.drugaMetoda()` wewnątrz tego samego obiektu. Nie przechodzi ono ponownie przez zewnętrzne proxy, więc advice przypisany wyłącznie do drugiej metody nie zadziała. Najczytelniejszym rozwiązaniem jest zwykle wydzielenie drugiej odpowiedzialności do osobnego beana. Samowstrzykiwanie proxy jest możliwe, ale silniej wiąże kod z frameworkiem i utrudnia rozumienie przepływu.

## Retry i logowanie — ważne granice

Aspekt retry nie może automatycznie ponawiać każdego wyjątku. Potrzebuje klasyfikacji błędów przejściowych, limitu prób, backoffu, jittera, budżetu czasu oraz idempotentnej operacji. Retry w wielu warstwach może zwielokrotnić ruch. Przykład pokazuje mechanizm przechwycenia, a pełniejszy kontrakt odporności znajduje się w Stage 3.

Aspekt logujący nie powinien bezrefleksyjnie zapisywać argumentów i wyników. Mogą zawierać hasła, tokeny, dane osobowe albo duże payloady. W produkcji potrzebne są redakcja, stabilny schemat zdarzeń, kontrola poziomu logowania i korelacja z trace’em.

## Jak testować

Logikę pomocniczą aspektu można testować jednostkowo, ale najważniejszy test jest integracyjny: wywołuje bean pobrany z kontekstu i sprawdza obserwowalny efekt proxy. Taki test wykrywa brak rejestracji aspektu, błędny pointcut, niewłaściwą kolejność oraz self-invocation — problemy niewidoczne przy bezpośrednim `new`.

Kod jest częścią głównego modułu `backend-engineering`.
