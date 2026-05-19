# Spring Data JPA

Spring Data JPA jest warstwą abstrakcji nad JPA i Hibernate, której celem jest uproszczenie dostępu do bazy danych. Framework eliminuje konieczność pisania dużej ilości boilerplate code odpowiedzialnego za obsługę `EntityManager`, transakcji czy podstawowych operacji CRUD. Dzięki repozytoriom Spring automatycznie generuje implementacje metod dostępu do danych na podstawie samych interfejsów.

Centralnym elementem Spring Data JPA są repozytoria. Najczęściej wykorzystuje się `JpaRepository`, które rozszerza `PagingAndSortingRepository` oraz `CrudRepository`. Dzięki temu repozytorium automatycznie udostępnia operacje takie jak `save`, `findById`, `findAll`, `delete`, a także mechanizmy paginacji i sortowania. Programista definiuje jedynie interfejs, natomiast Spring generuje implementację w czasie działania aplikacji.

Jedną z najważniejszych funkcjonalności Spring Data JPA jest query derivation, czyli automatyczne generowanie zapytań na podstawie nazw metod. Metody takie jak `findByLastName`, `findByAgeGreaterThan` czy `findByActiveTrue` są analizowane przez Spring, który generuje odpowiednie zapytania SQL lub JPQL. Pozwala to szybko budować proste zapytania bez konieczności ręcznego pisania SQL.

W bardziej zaawansowanych przypadkach można korzystać z adnotacji `@Query`, która pozwala definiować własne zapytania JPQL lub natywne SQL. JPQL operuje na encjach i polach klas, a nie bezpośrednio na tabelach bazodanowych. Spring wspiera również natywne SQL, co jest przydatne przy korzystaniu z funkcji specyficznych dla konkretnej bazy danych.

Repozytoria Spring Data JPA współpracują bezpośrednio z Hibernate, który pełni rolę implementacji ORM. Encje oznaczone `@Entity` są mapowane na tabele bazodanowe. Relacje pomiędzy encjami definiuje się za pomocą adnotacji takich jak `@OneToMany`, `@ManyToOne`, `@OneToOne` czy `@ManyToMany`.

Bardzo istotnym zagadnieniem w JPA jest strategia ładowania relacji, czyli fetch type. Domyślnie relacje `@ManyToOne` oraz `@OneToOne` są eager, co oznacza, że powiązane encje są pobierane od razu razem z główną encją. Natomiast `@OneToMany` oraz `@ManyToMany` domyślnie działają jako lazy loading. Oznacza to, że dane powiązane są pobierane dopiero w momencie pierwszego dostępu do relacji.

Mechanizm lazy loading jest wygodny, ale może prowadzić do jednego z najczęstszych problemów w Hibernate, czyli problemu N+1. Sytuacja pojawia się wtedy, gdy aplikacja pobiera listę encji głównych jednym zapytaniem, a następnie dla każdej encji wykonywane jest dodatkowe zapytanie do pobrania relacji lazy. W praktyce oznacza to jedno zapytanie główne oraz N dodatkowych zapytań, gdzie N jest liczbą rekordów. Problem ten bardzo negatywnie wpływa na wydajność aplikacji.

Najczęściej problem N+1 rozwiązuje się przy pomocy `JOIN FETCH` lub `@EntityGraph`. `JOIN FETCH` pozwala pobrać encję wraz z relacjami w jednym zapytaniu SQL. `@EntityGraph` działa podobnie, ale jest bardziej deklaratywnym mechanizmem definiowania relacji, które mają zostać pobrane eagerly dla konkretnego zapytania. Alternatywnie można używać projekcji DTO lub projekcji interfejsowych, aby pobierać wyłącznie potrzebne dane.

Spring Data JPA bardzo dobrze wspiera paginację i sortowanie. Mechanizm opiera się o interfejs `Pageable`, który zawiera informacje o numerze strony, rozmiarze strony oraz sortowaniu. Metody repozytoriów mogą przyjmować `Pageable` jako argument, a Spring automatycznie generuje odpowiednie zapytania SQL wraz z limitami i offsetami. Wynik zwracany jest jako obiekt `Page`, który oprócz danych zawiera również informacje o całkowitej liczbie rekordów, liczbie stron oraz aktualnej stronie.

`PagingAndSortingRepository` dostarcza podstawową obsługę paginacji i sortowania, natomiast `JpaRepository` rozszerza tę funkcjonalność o dodatkowe możliwości JPA, takie jak `flush()` czy batch operations. W praktyce `JpaRepository` jest najczęściej używanym typem repozytorium w aplikacjach Spring Boot.

Ważnym aspektem pracy z JPA są transakcje. Repozytoria Spring Data posiadają domyślne wsparcie dla transakcji, jednak najlepszą praktyką jest definiowanie granic transakcji na poziomie warstwy serwisowej. Dzięki temu logika biznesowa kontroluje cały przebieg operacji bazodanowych. Metody odczytowe często oznacza się jako `@Transactional(readOnly = true)`, co pozwala Hibernate zoptymalizować działanie poprzez pominięcie mechanizmu dirty checking.

Hibernate wykorzystuje mechanizm persistence context, który przechowuje zarządzane encje w ramach aktywnej sesji. Dzięki temu modyfikacja pól encji może zostać automatycznie wykryta i zsynchronizowana z bazą danych podczas commitowania transakcji. Mechanizm ten nazywa się dirty checking i jest jedną z najważniejszych funkcji ORM.

W większych projektach często stosuje się DTO projections lub interface-based projections. Zamiast pobierać pełne encje wraz z relacjami, aplikacja pobiera wyłącznie potrzebne pola. Pozwala to ograniczyć liczbę joinów, zmniejszyć ilość przesyłanych danych oraz poprawić wydajność aplikacji.

Mimo że Spring Data JPA znacząco upraszcza pracę z bazą danych, nie zawsze jest najlepszym rozwiązaniem. W przypadku bardzo złożonych zapytań lub krytycznych problemów wydajnościowych często stosuje się `JdbcTemplate`, natywny SQL lub rozwiązania takie jak MyBatis. ORM daje dużą wygodę, ale wymaga również świadomości tego, jakie zapytania faktycznie wykonuje Hibernate.

Spring Data JPA jest jednym z najważniejszych elementów ekosystemu Spring Boot. Umożliwia szybkie budowanie warstwy dostępu do danych, integrację z transakcjami oraz wygodne zarządzanie encjami i relacjami. Jednocześnie wymaga zrozumienia mechanizmów takich jak lazy loading, persistence context, dirty checking czy problem N+1, ponieważ większość problemów wydajnościowych w aplikacjach Springowych wynika właśnie z nieświadomego używania ORM.