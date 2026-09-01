# bean

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** bean.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „bean” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=BeanLifecycleContractTest,ProxyMechanicsTest" test`
> - **Role klas:** brak klasy-kontrprzykładu; pozostałe typy są minimalnymi modelami pojęć opisanych niżej.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## Spring bean lifecycle i mechanizm proxy



Spring zarządza definicją beana, utworzoną instancją oraz — opcjonalnie — proxy
zwracanym klientom. Te trzy rzeczy nie są tożsame. Adnotacja infrastrukturalna
na obiekcie utworzonym przez `new` nie uruchamia transakcji ani aspektu, ponieważ
wywołanie nie przechodzi przez kontener.

## Mapa przykładów

| Przykład | Co pokazuje |
| --- | --- |
| `LifecycleProbe` | obserwowalną kolejność inicjalizacji i niszczenia |
| `LifecycleObservationPostProcessor` | granice łańcucha `BeanPostProcessor` |
| `PaymentServiceImpl` | proxy interfejsowe i self-invocation |
| `CglibExampleService` | proxy klasowe |
| `FinalService` | finalna metoda, której CGLIB nie może nadpisać |
| `LoggingBeanAspect` | pointcut odnoszący się do rzeczywistego pakietu |

## Kolejność lifecycle

Dla typowego singletona kolejność jest następująca:

1. instancjonowanie — konstruktor lub factory method,
2. dependency injection właściwości, pól i metod,
3. callbacki `Aware`, np. `BeanNameAware`,
4. `BeanPostProcessor.postProcessBeforeInitialization`,
5. `@PostConstruct`, wykonywane przez jeden z post-processorów,
6. `InitializingBean.afterPropertiesSet()`,
7. własna metoda `initMethod`,
8. `BeanPostProcessor.postProcessAfterInitialization` — tutaj często pojawia się proxy,
9. bean jest gotowy do użycia,
10. `@PreDestroy`,
11. `DisposableBean.destroy()`,
12. własna metoda `destroyMethod`.

Punkt 4 jest łańcuchem wielu processorów. Kolejność dowolnego własnego
`BeanPostProcessor` względem obsługi `@PostConstruct` zależy od `PriorityOrdered`
i `Ordered`; nie należy zakładać kolejności rejestracji. Test
`BeanLifecycleContractTest` ustala ją jawnie dla tego laboratorium.

Spring wykonuje niszczenie singletonów przy kontrolowanym zamknięciu kontekstu.
Dla obiektu `prototype` kontener tworzy i inicjalizuje instancję, ale klient
odpowiada za jej dalszy lifecycle oraz zwolnienie zasobów.

## JDK proxy i CGLIB

| Właściwość | JDK dynamic proxy | CGLIB proxy |
| --- | --- | --- |
| Mechanizm | implementuje wskazane interfejsy | tworzy podklasę targetu |
| Typ widoczny klientowi | interfejs | klasa targetu |
| Metody bez interfejsu | niewidoczne przez proxy | mogą być przechwycone |
| `final` class/method | interfejs może nadal być proxowany | nie można nadpisać |
| `private` method | nie jest metodą kontraktu proxy | nie można nadpisać |

Sam fakt, że klasa implementuje interfejs, nie dowodzi rodzaju proxy. Spring AOP
może użyć obu strategii, a Spring Boot domyślnie preferuje proxy klasowe przez
`spring.aop.proxy-target-class=true`. `ProxyMechanicsTest` wybiera strategię
jawnie i sprawdza ją przez `AopUtils`.

## Self-invocation

Klient wywołuje `proxy.internalCall()`, ale wewnętrzne `this.pay()` jest już
zwykłym wywołaniem na target object:

```text
client -> proxy -> target.internalCall() -> this.pay()
                                      bypass proxy ---^
```

Dlatego interceptor widzi jedno wywołanie, nie dwa. Dotyczy to m.in.
`@Transactional`, `@Async`, `@Cacheable`, `@Retryable` i method security, jeśli
mechanizm działa przez Spring AOP.

Najczytelniejszą naprawą jest wydzielenie drugiej odpowiedzialności do osobnego
beana i wykonanie wywołania przez jego publiczny kontrakt. Self-injection albo
`AopContext.currentProxy()` wiążą kod z mechanizmem proxy i zwykle utrudniają
testowanie. AspectJ weaving ma inne własności, ale nie jest domyślnym modelem
Spring AOP.

## Czego nie robić w callbackach

- Nie wykonuj długich wywołań sieciowych w `@PostConstruct`; blokują gotowość.
- Nie zakładaj, że każdy inny bean jest już w pełni zainicjalizowany.
- Nie uruchamiaj ręcznie niekontrolowanych wątków bez symetrycznego shutdownu.
- Nie polegaj wyłącznie na `@PreDestroy` podczas awarii procesu lub `SIGKILL`.
- Nie wywołuj metod biznesowych na `this`, jeśli oczekujesz działania aspektu.

## Testy

`BeanLifecycleContractTest` uruchamia minimalny `AnnotationConfigApplicationContext`
i sprawdza pełną kolejność. `ProxyMechanicsTest` nie ładuje Spring Boota — tworzy
proxy przez `ProxyFactory` i pokazuje różnice JDK/CGLIB, self-invocation oraz
finalną metodę. Testowanie obiektu utworzonego przez `new` nie wystarcza do
potwierdzenia, że deklaratywna infrastruktura została poprawnie podłączona.
