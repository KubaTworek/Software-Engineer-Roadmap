# Transakcje i JPA

[Spis rozdziałów](README.md) · [Książki i źródła](MATERIALY.md#07-transakcje-i-jpa)

<!-- question-toc:start -->
## Spis pytań

- [TX-01. Jak `@Transactional` działa wewnętrznie?](#tx-01-jak-transactional-działa-wewnętrznie)
- [TX-02. Co przy self-invocation: `placeOrder()` wywołuje własne `audit(REQUIRES_NEW)`?](#tx-02-co-przy-self-invocation-placeorder-wywołuje-własne-auditrequires_new)
- [TX-03. Czy transakcja zadziała na metodzie private albo public final?](#tx-03-czy-transakcja-zadziała-na-metodzie-private-albo-public-final)
- [TX-04. Czym różnią się REQUIRED, REQUIRES_NEW i NESTED?](#tx-04-czym-różnią-się-required-requires_new-i-nested)
- [TX-05. Serwis bez transakcji zapisuje zamówienie, a zapis płatności rzuca wyjątek. Czy zamówienie zostaje?](#tx-05-serwis-bez-transakcji-zapisuje-zamówienie-a-zapis-płatności-rzuca-wyjątek-czy-zamówienie-zostaje)
- [TX-06. Bean B rzuca RuntimeException, a transakcyjny A go łapie. Co zostanie zatwierdzone?](#tx-06-bean-b-rzuca-runtimeexception-a-transakcyjny-a-go-łapie-co-zostanie-zatwierdzone)
- [TX-07. Czy zmiana encji bez `save()` zostanie zapisana? Czym są flush i commit?](#tx-07-czy-zmiana-encji-bez-save-zostanie-zapisana-czym-są-flush-i-commit)
- [TX-08. Czy rollback wywołującego cofnie zapis wykonany przez `@Async`?](#tx-08-czy-rollback-wywołującego-cofnie-zapis-wykonany-przez-async)
- [TX-09. Czy e-mail z `@EventListener` zostanie cofnięty po rollbacku?](#tx-09-czy-e-mail-z-eventlistener-zostanie-cofnięty-po-rollbacku)
- [TX-10. Wewnętrzna metoda deklaruje SERIALIZABLE, ale dołącza do transakcji. Jaka izolacja obowiązuje?](#tx-10-wewnętrzna-metoda-deklaruje-serializable-ale-dołącza-do-transakcji-jaka-izolacja-obowiązuje)
- [TX-11. Czy dwie transakcje mogą pobrać po 400 z salda 500 mimo `@Transactional`?](#tx-11-czy-dwie-transakcje-mogą-pobrać-po-400-z-salda-500-mimo-transactional)
- [TX-12. Jak dobrać optimistic i pessimistic locking, np. dla magazynu?](#tx-12-jak-dobrać-optimistic-i-pessimistic-locking-np-dla-magazynu)
- [TX-13. Jak działają EntityManager, Session, SessionFactory i Spring Data JPA?](#tx-13-jak-działają-entitymanager-session-sessionfactory-i-spring-data-jpa)
- [TX-14. CrudRepository czy JpaRepository?](#tx-14-crudrepository-czy-jparepository)
- [TX-15. Czym różnią się `persist()` i `merge()`?](#tx-15-czym-różnią-się-persist-i-merge)
- [TX-16. Lazy czy eager i skąd LazyInitializationException tylko na produkcji?](#tx-16-lazy-czy-eager-i-skąd-lazyinitializationexception-tylko-na-produkcji)
- [TX-17. Czym jest N+1 i jak wybrać rozwiązanie?](#tx-17-czym-jest-n1-i-jak-wybrać-rozwiązanie)
- [TX-18. Czym różnią się cache pierwszego i drugiego poziomu w Hibernate?](#tx-18-czym-różnią-się-cache-pierwszego-i-drugiego-poziomu-w-hibernate)
- [TX-19. Jak działa paginacja od kontrolera przez JPA do bazy?](#tx-19-jak-działa-paginacja-od-kontrolera-przez-jpa-do-bazy)
- [TX-20. Czym różnią się cascade i orphanRemoval?](#tx-20-czym-różnią-się-cascade-i-orphanremoval)
- [TX-21. Dlaczego rollback może zostawić częściowe dane po błędzie drugiego z trzech zapisów?](#tx-21-dlaczego-rollback-może-zostawić-częściowe-dane-po-błędzie-drugiego-z-trzech-zapisów)
<!-- question-toc:end -->

## TX-01. Jak `@Transactional` działa wewnętrznie?

W domyślnym trybie proxy interceptor odczytuje metadane i przez wybrany transaction manager rozpoczyna lub dołącza transakcję, wywołuje metodę i podejmuje commit/rollback. W modelu imperatywnym zasoby zwykle wiązane są z wątkiem; reactive używa innego kontekstu.

Adnotacja nie obejmuje automatycznie HTTP, e-maila ani wszystkich baz. Domyślnie rollback wywołują RuntimeException i Error; reguły checked exceptions można zmienić lokalnie, a w nowszych wersjach także globalnie. [Semantyka adnotacji](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html).

Granica transakcji powinna odpowiadać lokalnemu przypadkowi użycia, np. zapisowi zamówienia i jego pozycji. Zbyt wąska granica może zostawić pół operacji, a zbyt szeroka utrzymywać połączenie i locki podczas HTTP lub generowania pliku.

Szkic wywołania przez osobny bean:

<!-- example:TX-01-1 kind:spring-sketch -->
**Kontekst:** Szkic Spring — wymaga zależności, konfiguracji i ewentualnych typów domenowych; poza automatyczną kompilacją.

~~~java
@Transactional
public void confirm(OrderId id) {
    var order = orders.findRequired(id);
    order.confirm();
    outbox.append(OrderConfirmed.from(order));
}
~~~

Typy domenowe są przykładowe. Poprawność wymaga jednego managera i tego samego transakcyjnego magazynu dla orders oraz outbox. Adnotacja nie sprawdza biznesowego niezmiennika; robi to kod/constraints. Test powinien wymusić błąd po pierwszej zmianie i odczytać wynik z nowej transakcji.

## TX-02. Co przy self-invocation: `placeOrder()` wywołuje własne `audit(REQUIRES_NEW)`?

Przyjmijmy, że klient wywołał placeOrder przez proxy i rozpoczął T1. Własne wywołanie this.audit() omija interceptor, więc REQUIRES_NEW na audit nie tworzy T2; kod działa w kontekście T1. Gdy nie było zewnętrznej transakcji, wewnętrzna adnotacja sama jej nie rozpocznie.

**Błędna granica — oczekiwano niezależnej transakcji audytu:**

<!-- example:TX-02-1 kind:spring-sketch -->
**Kontekst:** Szkic Spring — wymaga zależności, konfiguracji i ewentualnych typów domenowych; poza automatyczną kompilacją.

~~~java
@Transactional
public void placeOrder() {
    audit(); // zwykłe wywołanie na this
}
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void audit() {
    auditRepository.saveAttempt();
}
~~~

**Poprawna granica dla niezależnego audytu — wywołanie przez proxy innego beana:**

<!-- example:TX-02-2 kind:spring-sketch -->
**Kontekst:** Szkic Spring — wymaga zależności, konfiguracji i ewentualnych typów domenowych; poza automatyczną kompilacją.

~~~java
@Transactional
public void placeOrder() {
    auditService.recordAttempt(); // wstrzyknięty bean
}
// W osobnym beanie AuditService:
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void recordAttempt() {
    auditRepository.saveAttempt();
}
~~~

Oba bloki są szkicami Spring z domenowym repozytorium. Alternatywą jest jawny TransactionTemplate; AspectJ weaving ma inne reguły niż domyślne proxy. Niezależna T2 może nie widzieć zapisów T1 lub czekać na jego lock, więc audyt powinien używać danych niezależnych od niezatwierdzonego zamówienia. Test integracyjny sprawdza wynik po rollbacku T1, nie tylko wywołanie auditService.

<!-- follow-up:TX-02 -->
**Sprawdź rozumienie:** Audit jest już osobnym beanem REQUIRES_NEW, ale czeka na wiersz zmieniany przez zewnętrzną T1. Co to zmienia?

<details>
<summary>Kierunek odpowiedzi</summary>

Proxy tworzy T2, ale nie usuwa locków ani ograniczeń widoczności. T2 może czekać na T1, a T1 na wynik T2. Użyj danych audytu niezależnych od niezatwierdzonego zapisu i sprawdź timeouty/pulę.

</details>

## TX-03. Czy transakcja zadziała na metodzie private albo public final?

Private nie jest przechwytywana przez zwykłe proxy. Public final nie może być nadpisana przez proxy klasowe CGLIB, więc jej advice nie zadziała tą drogą. JDK proxy może przechwycić wywołanie metody interfejsu, nawet jeżeli implementacja jest finalna.

Wynik zależy zatem od rodzaju proxy i sposobu wywołania, nie tylko modyfikatora. Przyjmuję publiczny kontrakt osobnego beana i testuję granicę. [Ograniczenia mechanizmów proxy](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).

Test powinien obejmować dokładną drogę klienta: interfejs JDK proxy i implementację finalną albo proxy klasowe oraz metodę finalną. Te przypadki nie są równoważne. Wstrzyknięcie konkretnej klasy zamiast interfejsu również może zmienić dostępność proxy.

Publiczna fasada wywołująca private helper może być transakcyjna; helper działa wtedy w jej kontekście, choć własne advice private nie zostało zastosowane. „Private nie jest proxy'owane” nie oznacza więc „private nigdy nie wykonuje się w transakcji”. Rozróżnienie aktywnego kontekstu od przechwycenia konkretnej metody zapobiega błędnym diagnozom.

## TX-04. Czym różnią się REQUIRED, REQUIRES_NEW i NESTED?

Propagacja określa relację z już aktywną transakcją:

| Wariant | Granica fizyczna | Skutek rollbacku zewnętrznego |
| --- | --- | --- |
| REQUIRED | dołącza do istniejącej albo tworzy nową | obejmuje wspólne zmiany |
| REQUIRES_NEW | zawiesza zewnętrzną i tworzy osobną | nie cofa już zatwierdzonej wewnętrznej |
| NESTED | savepoint jednej fizycznej transakcji, jeśli wspierany | cofa także zmiany za savepointem |

REQUIRES_NEW pasuje do świadomie niezależnego zapisu próby, ale nie do danych mających być atomową częścią zamówienia. Zewnętrzna transakcja zwykle nadal zajmuje połączenie; wiele równoczesnych wywołań proszących o drugie może wyczerpać pulę.

NESTED nie jest przenośną obietnicą JPA. Obsługa savepointów zależy od managera i zasobu, a cofnięcie DB nie gwarantuje automatycznego przywrócenia całego stanu persistence context. Wybór sprawdzam na faktycznym managerze i testuję konflikt, błąd wewnętrzny oraz finalny commit/rollback. [Propagacja Spring](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html).

## TX-05. Serwis bez transakcji zapisuje zamówienie, a zapis płatności rzuca wyjątek. Czy zamówienie zostaje?

Przy braku zewnętrznej transakcji każde transakcyjne save repozytorium może zakończyć własną transakcję. Wówczas orderRepo.save() jest już zatwierdzone, gdy paymentRepo.save() zawodzi. RuntimeException w serwisie nie wycofa wstecz zamkniętego commitu. Wynik zależy od rzeczywistego managera, konfiguracji repozytoriów i drogi wywołania.

Jeżeli oba zapisy należą do jednego lokalnego niezmiennika w tej samej DB, umieszczam je w transakcyjnej fasadzie serwisu. Test wymusza błąd drugiego zapisu i odczytuje stan z nowej transakcji: nie powinno pozostać pół zamówienia.

Jeżeli płatność oznacza HTTP do operatora, wspólna adnotacja nie daje atomowości. Fasada utrwala zamiar i status, a trwały proces z idempotencją, sprawdzaniem wyniku i kompensacją domyka integrację. Mock repozytorium może potwierdzić argumenty lub kolejność wywołań, ale nie dowodzi rzeczywistego commitu, rollbacku i zachowania po crashu.

## TX-06. Bean B rzuca RuntimeException, a transakcyjny A go łapie. Co zostanie zatwierdzone?

Przy REQUIRED i tym samym managerze A rozpoczyna T1, a B do niej dołącza. RuntimeException przechodzący przez interceptor B zgodnie z regułami rollbacku może oznaczyć T1 jako rollback-only. A łapie wyjątek, lecz nie usuwa tego znacznika: próba commitu kończy się rollbackiem i zwykle UnexpectedRollbackException.

Przebieg „A zwróciło normalnie” nie oznacza więc „DB zatwierdziła”. Co innego, gdy B przechwyci błąd przed wyjściem przez interceptor: wtedy advice może go nie zobaczyć. Zasób DB nadal mógł już oznaczyć transakcję jako nieudaną, więc samo catch nie jest uniwersalnym sposobem odzyskania.

Dla REQUIRES_NEW wynik B jest niezależny i trzeba opisać go osobno. Najpierw ustalam, czy błąd ma anulować cały przypadek użycia, czy zostać oddzielnym procesem. Audyt po błędzie zapisuję w świadomie niezależnej granicy, a test sprawdza finalny stan z nowego połączenia/kontekstu.

## TX-07. Czy zmiana encji bez `save()` zostanie zapisana? Czym są flush i commit?

Zarządzana encja jest śledzona w persistence context; dirty checking wykrywa zmianę i przy flush generuje SQL. W aktywnej poprawnie skonfigurowanej transakcji nie trzeba ponawiać save dla każdej zmiany. Encja detached nie ma tej gwarancji, a tryb read-only może zmieniać zachowanie providera.

Flush synchronizuje stan z DB, ale nie jest commitem i SQL nadal może zostać wycofany. Błąd constraint może pojawić się dopiero wtedy. Nie traktuję wczesnego wygenerowania ID jako dowodu zatwierdzenia.

Dirty checking dotyczy encji managed:

<!-- example:TX-07-1 kind:jpa-sketch -->
**Kontekst:** Szkic JPA/Spring Data — wymaga encji, repozytorium lub persistence context; poza automatyczną kompilacją.

~~~java
@Transactional
public void changeEmail(long id, String email) {
    var user = entityManager.find(User.class, id);
    user.changeEmail(email);
    // brak save: zmiana managed User zostanie wykryta przy flush
}
~~~

To szkic JPA z domenowym User. Jeśli metoda zwróci encję, a caller zmieni ją po zamknięciu kontekstu, nie mamy tej samej gwarancji. Flush może też nastąpić przed zapytaniem, nie tylko na końcu. Dlatego błąd constraint bywa zgłoszony przy pozornie niezwiązanym odczycie i wymaga analizy faktycznie wysłanego SQL.

## TX-08. Czy rollback wywołującego cofnie zapis wykonany przez `@Async`?

Nie automatycznie. Task na innym wątku nie uczestniczy w imperatywnej transakcji wywołującego. Może wykonać własną transakcję i zatwierdzić zapis, nawet gdy oryginalna operacja upadnie. Timing nie zmienia granicy atomowości.

Jeśli skutek ma wynikać z zatwierdzonego stanu, publikuję trwały zamiar przez outbox w tej samej transakcji. Jeśli zapis ma być atomowy z operacją główną, wykonuję go w jej lokalnej granicy, bez przypadkowego przejścia async.

Task może ruszyć przed commitem, odczytać brak danych T1 i uznać zamówienie za nieistniejące. Może też zatwierdzić niezależny zapis, po czym T1 się wycofa. To dwa różne objawy tej samej brakującej koordynacji lifecycle.

Jeżeli asynchroniczny handler potrzebuje zatwierdzonego ID, zdarzenie powinno wynikać z trwałego commitu, a dane dostępne z jego źródła. Po restarcie proces ma dać się odtworzyć. Nie próbuję „naprawić propagacji” przez ręczne kopiowanie EntityManager lub ThreadLocal do zadania: zasób transakcyjny ma własny kontrakt wątkowy i lifecycle.

## TX-09. Czy e-mail z `@EventListener` zostanie cofnięty po rollbacku?

Zwykły synchroniczny listener może wysłać e-mail przed commitem. Rollback DB nie cofnie wiadomości u dostawcy. `@TransactionalEventListener(AFTER_COMMIT)` pozwala poczekać na commit, ale sama publikacja w pamięci nie gwarantuje przetrwania crashu między commitem a obsługą.

Dla niezawodnego efektu używam outbox i retry z deduplikacją. Ustalam też zachowanie listenera bez aktywnej transakcji i sposób raportowania błędu. „After commit” i trwała dostawa to dwie różne własności.

Email wysłany przed commitem może potwierdzić zakup, który nigdy nie powstał. AFTER_COMMIT usuwa ten konkretny problem, ale proces może zginąć zaraz po commicie, zanim listener wyśle wiadomość. Niezawodność wymaga zapisanego zamiaru, który inny worker podejmie po restarcie.

Przykład testu awarii ma dwa punkty: przed commitem nie powinno być trwałego joba do wysyłki, po commicie job powinien istnieć nawet bez działającego workera. Unikalny klucz powiadomienia ogranicza duplikaty. Ewentualny zapis DB wykonywany przez listener po commicie musi mieć jawną nową transakcję, jeśli ma zostać zatwierdzony.

<!-- follow-up:TX-09 -->
**Sprawdź rozumienie:** AFTER_COMMIT wykonał się poprawnie w testach. Co przy crashu po commicie, przed wysłaniem emaila?

<details>
<summary>Kierunek odpowiedzi</summary>

Listener w pamięci może zniknąć. Trwały outbox zapisuje zamiar razem z domeną, a worker ponawia z deduplikacją. After commit określa kolejność, nie gwarantuje trwałego dostarczenia.

</details>

## TX-10. Wewnętrzna metoda deklaruje SERIALIZABLE, ale dołącza do transakcji. Jaka izolacja obowiązuje?

Przy REQUIRED dołączającym do istniejącej transakcji obowiązują charakterystyki tej transakcji. Samo isolation na metodzie wewnętrznej nie podnosi poziomu fizycznego połączenia. Walidacja istniejącej transakcji może wymusić zgłoszenie niezgodności zamiast jej zignorowania.

Poziom ustanawiam na granicy tworzącej transakcję. REQUIRES_NEW pozwala utworzyć inną, ale zmienia atomowość i wykorzystanie zasobów. Nie stosuję go tylko po to, żeby „wymusić adnotację”.

Wewnętrzny Serializable na REQUIRED nie „podkręca” poziomu aktywnego połączenia. Kod działa na snapshotach i lockach zewnętrznej transakcji, chyba że manager odrzuci niezgodność. To może wyjaśnić test, który przechodzi przy samodzielnym wywołaniu B, a zawodzi przez A.

Sprawdzam zatem izolację na granicy, która faktycznie rozpoczyna pracę, oraz konfigurację walidacji managera. Zmiana na REQUIRES_NEW oddziela commit i widoczność, co może być biznesowo niepoprawne. Alternatywnie niezmiennik da się wymusić constraintem lub atomowym SQL bez podnoszenia izolacji całego długiego przepływu.

## TX-11. Czy dwie transakcje mogą pobrać po 400 z salda 500 mimo `@Transactional`?

Tak, przy zwykłym read-check-write odpowiednia izolacja i ochrona niezmiennika nie wynikają z samej adnotacji. Obie operacje mogą sprawdzić stare saldo, a następnie nadpisać wynik. Można też otrzymać inną anomalię zależnie od SQL i silnika.

Rozwiązaniem bywa warunkowe `UPDATE ... SET balance=balance-:amount WHERE balance>=:amount` i kontrola liczby zmienionych wierszy, versioning lub `SELECT FOR UPDATE`. Dla wielu kont ustalam kolejność blokad i ledger. Po konflikcie ponawiam cały przypadek na świeżym stanie.

Dla pojedynczego konta warunkowa aktualizacja łączy sprawdzenie i zmianę:

<!-- example:TX-11-1 kind:sql-template -->
**Kontekst:** Szablon SQL — wymaga bazy, schematu i podstawienia parametrów; skrypt go nie wykonuje.

~~~sql
UPDATE account
SET balance_minor = balance_minor - :amount
WHERE id = :account_id
  AND :amount > 0
  AND balance_minor >= :amount;
~~~

Aplikacja wymaga dokładnie jednego zmienionego wiersza; zero oznacza odmowę lub brak konta według jawnego kontraktu. Wpis księgowy powinien powstać w tej samej lokalnej transakcji. Dla transferu między kontami dochodzą reguły waluty, kolejność blokowania i tożsamość operacji. Sama odpowiedź 200 bez kontroli liczby wierszy może ukryć nieudane pobranie.

## TX-12. Jak dobrać optimistic i pessimistic locking, np. dla magazynu?

Optimistic locking porównuje wersję przy zapisie; brak zgodności wykrywa konflikt, np. przez `@Version`. Sprawdza się przy rzadszych konfliktach. Pessimistic locking blokuje rekord w DB na czas transakcji, co ogranicza przeploty kosztem oczekiwania i ryzyka deadlocku.

Dla popularnego zapasu często prostsza jest atomowa aktualizacja warunkowa. Dobieram rozwiązanie do niezmiennika i konfliktów, nie według reguły „optimistic zawsze szybszy”. Retry jest ograniczony, dotyczy całej transakcji i nie powtarza nieodwracalnych efektów zewnętrznych.

Przy optimistic locking SQL aktualizacji zawiera oczekiwaną wersję. Dwa requesty zaczynają od wersji 7: pierwszy zapisuje 8, drugi aktualizuje zero wierszy i zgłasza konflikt. Retry musi ponownie pobrać dane i przeliczyć decyzję, a nie tylko wysłać ten sam stary update.

Pessimistic lock może być uzasadniony dla krótkiej operacji o wysokim contention, ale oczekiwanie i timeouty stają się częścią UX. Wydłużanie sekcji przez HTTP pogarsza przepustowość. Dla inventory porównuję procent konfliktów, długość transakcji, popularność pojedynczego SKU i koszt odrzuconego/ponowionego zamówienia.

## TX-13. Jak działają EntityManager, Session, SessionFactory i Spring Data JPA?

EntityManager to API JPA do pracy z persistence context; EntityManagerFactory tworzy takie konteksty. Hibernate jest providerem JPA i oferuje natywne Session oraz SessionFactory, do których można zejść przez unwrap. SessionFactory jest współdzielona, kontekst encji nie nadaje się do dowolnego współdzielenia między wątkami.

Spring Data tworzy implementację/proxy repozytorium używającą EntityManager; provider generuje SQL, a sterownik JDBC komunikuje się z DB przez DataSource. Spring JDBC może ominąć JPA. To warstwy odpowiedzialności, nie konkurencyjne nazwy tego samego obiektu.

Wstrzyknięty EntityManager w Springu często jest proxy, które wybiera kontekst związany z bieżącą transakcją. Można więc bezpiecznie wstrzyknąć takie proxy do singletona, ale nie oznacza to, że konkretny persistence context albo encje wolno dowolnie współdzielić między wątkami.

Persistence context jest identity mapą i jednostką śledzenia zmian, nie samym fizycznym połączeniem. Connection może być pobrane zgodnie ze strategią providera. SessionFactory/EntityManagerFactory są kosztownymi współdzielonymi fabrykami; tworzenie ich per request niszczyłoby korzyść lifecycle. Diagnozując SQL, przechodzę kolejno przez repozytorium, ORM, JDBC, pulę i bazę.

## TX-14. CrudRepository czy JpaRepository?

CrudRepository udostępnia bazowe operacje CRUD. JpaRepository rozszerza repozytoria Spring Data o funkcje specyficzne dla JPA, m.in. flush i operacje batch, oraz kontrakty list/paginacji właściwe dla używanej wersji Spring Data.

Nie zakładam, że `saveAll` oznacza pojedynczy SQL lub aktywne JDBC batching. Wybieram minimalny kontrakt potrzebny warstwie aplikacyjnej; batch delete może ominąć lifecycle encji i zostawić persistence context niezgodny ze stanem DB.

saveAll iteruje po encjach zgodnie z implementacją repozytorium, a dopiero ORM/sterownik mogą pogrupować zgodne instrukcje. Duży rozmiar kolekcji nie gwarantuje jednego round trip ani małej retencji w kontekście.

Metody deleteAllInBatch wykonują inną klasę operacji niż usuwanie każdej encji: mogą ominąć callbacki i nie odświeżyć już zarządzanych obiektów. Nazwa „batch” nie przesądza więc semantyki domenowej. Warstwa aplikacyjna powinna widzieć potrzebny kontrakt repozytorium, a nie całą kolekcję metod administracyjnych tylko dlatego, że interfejs biblioteki je udostępnia.

## TX-15. Czym różnią się `persist()` i `merge()`?

Persist wprowadza nową encję do kontekstu; merge kopiuje stan przekazanego obiektu do zarządzanej instancji i zwraca tę instancję. Oryginalny obiekt detached nie staje się przez to automatycznie zarządzany.

Spring Data save wybiera ścieżkę na podstawie rozpoznania nowości encji. Bezrefleksyjny merge całego obiektu z API może nadpisać pola nowszym lub niepełnym stanem. Ładuję agregat i stosuję jawne zmiany z kontrolą wersji.

Merge należy czytać jako „skopiuj stan do managed”, a nie „załącz tę samą instancję”:

<!-- example:TX-15-1 kind:jpa-sketch -->
**Kontekst:** Szkic JPA/Spring Data — wymaga encji, repozytorium lub persistence context; poza automatyczną kompilacją.

~~~java
User managed = entityManager.merge(detached);
// detached i managed mogą być różnymi obiektami
managed.changeEmail("new@example.test");
~~~

To szkic z encją User. Powrót do modyfikowania detached może dać złudzenie, że „merge przestał działać”. Dodatkowym ryzykiem jest skopiowanie null z niepełnego DTO na istniejące pole. Dlatego w API aktualizacji zwykle pobieram agregat i wywołuję jawne metody zmiany, uwzględniając oczekiwaną wersję oraz dozwolone pola.

## TX-16. Lazy czy eager i skąd LazyInitializationException tylko na produkcji?

Lazy odracza pobranie danych, eager wymaga ich dostępności zgodnie z mappingiem, ale nie gwarantuje jednego JOIN. Dostęp do niezaładowanej relacji poza działającym kontekstem/session może rzucić jawny wyjątek. Nie jest to „zawsze cichy” błąd.

Różnice OSIV, transakcyjnych testów, danych i serializacji tłumaczą rozbieżność środowisk. Pobieram dane potrzebne use case przez fetch plan/projekcję i tworzę DTO w granicy odczytu. Włączenie wszędzie eager lub OSIV może ukryć N+1 i powiększyć koszt.

Różnica środowisk często wynika z testu otaczającego całość transakcją. Kontroler i serializacja odczytują lazy relację, bo testowy kontekst nadal jest otwarty; na produkcji serwis zwrócił encję detached i getter wybucha. OSIV może z kolei zamaskować błąd dodatkowym SQL po wyjściu z serwisu.

Projektuję wynik use case jako dokładny DTO i fetch plan obejmujący jego pola. Nie pobieram całego grafu „na wszelki wypadek”. Test integracyjny powinien obejmować rzeczywistą granicę HTTP/serializacji i policzyć SQL, a nie tylko sprawdzić getter przed zakończeniem transakcyjnego testu.

## TX-17. Czym jest N+1 i jak wybrać rozwiązanie?

Jedno zapytanie pobiera N obiektów, a kolejne odczyty relacji generują N dodatkowych zapytań. Potwierdzam liczbą SQL na request i trace, zamiast diagnozować tylko po czasie. Problem może dotyczyć relacji lazy i eager.

Fetch join, entity graph, batch fetching lub DTO projection dobieram do potrzebnego kształtu danych. Fetch join kolekcji z paginacją może zwielokrotnić wiersze i spowodować paginację w pamięci. Czasem lepsze są dwa ograniczone zapytania: ID strony, potem szczegóły.

Dla 100 zamówień jeden odczyt listy i osobny klient dla każdego daje 101 zapytań; jeśli każdy odczyt ma 5 ms kosztu sieciowego, sama komunikacja może zdominować request. Wspólny fetch lub projekcja ogranicza liczbę round trips, lecz szeroki JOIN może powielić dane rodzica.

Szkic zapytania JPQL dla konkretnego potrzebnego powiązania:

<!-- example:TX-17-1 kind:jpql-template -->
**Kontekst:** Szablon JPQL — nazwy oznaczają encje i relacje, nie tabele SQL; wymaga skonfigurowanego JPA.

~~~jpql
select o from Order o
join fetch o.customer
where o.id in :ids
~~~

To JPQL, nie bezpośredni SQL tabel. Przy kilku kolekcjach lub paginacji sprawdzam kardynalność i plan. „Jedno zapytanie” nie musi być tańsze od dwóch dobrze ograniczonych.

## TX-18. Czym różnią się cache pierwszego i drugiego poziomu w Hibernate?

L1 jest związany z persistence context i zapewnia tożsamość zarządzanej encji w jego obrębie. L2 jest opcjonalny, współdzielony między kontekstami fabryki i wymaga providera oraz strategii zgodnej z aktualizacjami. Query cache jest odrębnym mechanizmem.

L1 nie jest ogólnym cache aplikacji, a L2 nie eliminuje wszystkich SQL. Aktualizacja poza Hibernate może zostawić nieaktualne wpisy bez dodatkowego protokołu invalidacji. Cache dobieram do danych i wymaganej spójności, mierząc hit ratio i koszt utrzymania.

L1 może zwrócić tę samą zarządzaną instancję ponownie, mimo że inna transakcja zmieniła rekord. To własność kontekstu i odczytu, nie dowód awarii replikacji. Refresh lub nowa transakcja mają inne konsekwencje niż ponowne find na tej samej instancji.

L2 wymaga polityki dla zapisów i ewikcji. Dobrze pasują rzadko zmieniane słowniki, gorzej gorące dane z wieloma zewnętrznymi writerami. Query cache przechowuje wynik zapytania według własnego modelu i wymaga prawidłowej invalidacji. Mierzę trafienia razem z kosztem utrzymania, nie tylko liczbą „unikniętych” odczytów.

## TX-19. Jak działa paginacja od kontrolera przez JPA do bazy?

Kontroler przyjmuje page/size/sort lub cursor, waliduje limity i przekazuje kryteria do serwisu. Repozytorium wykorzystuje Pageable albo jawne warunki; provider generuje SQL z limitem/offsetem właściwym dla dialektu. To DB ogranicza wynik, jeśli nie wymuszono paginacji w pamięci.

Page może wymagać osobnego count; Slice pozwala ograniczyć ten koszt. Dla milionów wierszy wybieram keyset i stabilne sortowanie z unikalnym tie-breakerem. Nie pobieram wszystkich rekordów do listy, żeby następnie zwrócić pierwsze dziesięć.

Dla sortowania po createdAt muszę dodać unikalny ID, inaczej dwa rekordy z tą samą datą mogą zamieniać się stronami. Parametry sortowania mapuję na allowlistę, ponieważ koszt sortu i sposób dostępu są częścią budżetu API.

<!-- example:TX-19-1 kind:jpa-sketch -->
**Kontekst:** Szkic JPA/Spring Data — wymaga encji, repozytorium lub persistence context; poza automatyczną kompilacją.

~~~java
PageRequest page = PageRequest.of(0, 10,
    Sort.by("createdAt").descending().and(Sort.by("id").descending()));
~~~

To fragment Spring Data. Pierwsza strona ma indeks 0. Page może uruchomić count całego zbioru, choć lista ma tylko dziesięć elementów. Dla dużych, stale zmienianych zbiorów cursor/keyset daje bardziej przewidywalny koszt niż rosnący offset.

## TX-20. Czym różnią się cascade i orphanRemoval?

Cascade propaguje określone operacje EntityManager na powiązane encje, np. persist lub remove. OrphanRemoval usuwa dziecko po odłączeniu go od relacji reprezentującej jego właściciela, zgodnie z semantyką mappingu. Nie jest tym samym co DB `ON DELETE CASCADE`.

Stosuję je dla rzeczywistego ownershipu lifecycle. Remove na relacji do współdzielonego obiektu, np. produktu w zamówieniach, może usunąć za dużo. Testuję także zmianę kolekcji, flush i SQL, a nie jedynie stan obiektu w pamięci.

Przykład ownershipu: pozycja zamówienia nie ma sensu poza zamówieniem, więc usunięcie jej z kolekcji może oznaczać usunięcie rekordu przez orphanRemoval. Produkt z katalogu jest natomiast współdzielony i odłączenie pozycji nie powinno go usuwać.

Cascade persist pomaga zapisać nowy agregat wraz z dziećmi, lecz nie oznacza, że każda relacja powinna mieć ALL. Przy relacji dwukierunkowej aktualizuję obie strony zgodnie z owning side. Test sprawdza SQL po flush i odczyt z nowego kontekstu; sama zmiana kolekcji w pamięci nie dowodzi właściwego DELETE.

## TX-21. Dlaczego rollback może zostawić częściowe dane po błędzie drugiego z trzech zapisów?

Sprawdzam przejście przez proxy, aktywną transakcję, manager, propagację, obsługę wyjątku, autocommit i transakcyjność tabel. Checked exception lub połknięty błąd może nie wywołać oczekiwanego rollbacku. REQUIRES_NEW i drugi wątek mają osobne granice.

Trzy zapisy w jednej właściwej lokalnej transakcji powinny być atomowe; sekwencje, logi zewnętrzne i API nie muszą się cofać. Odtwarzam awarię w teście integracyjnym i odczytuję wynik z nowej transakcji, żeby uniknąć mylącego cache L1.

Do diagnozy zapisuję, gdzie faktycznie nastąpiły commity. Log przed końcem metody nie jest dowodem zatwierdzenia, a flush dwóch tabel nie oznacza, że trzecia jest już poza tą samą transakcją. Odpowiedź zależy od managera, tabel, advice i rzeczywistego wyjątku.

Przypadek odtwarzam na prawdziwej DB używanej przez moduł: wymuszam constraint violation po drugim zapisie, wychodzę z serwisu i odczytuję trzy tabele w nowej transakcji. Jeżeli istnieje zewnętrzny skutek, sprawdzam go osobno. In-memory mock z reguły nie odwzoruje autocommit, rollback-only ani blokad.

<!-- labs:start -->
## Powiązane laboratoria

Poniższe materiały rozwijają wybrane mechanizmy rozdziału. Każde laboratorium ma własne założenia, ograniczenia i komendy weryfikacji.

- [transactional](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/transactional/README.md)
- [jpa](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/jpa/README.md)
- [lock](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/lock/README.md)
<!-- labs:end -->
