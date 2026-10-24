# Podstawy Javy

[Spis rozdziałów](README.md) · [Książki i źródła](MATERIALY.md#01-podstawy-javy)

<!-- question-toc:start -->
## Spis pytań

- [JAVA-01. Czym różnią się JVM, JRE i JDK?](#java-01-czym-różnią-się-jvm-jre-i-jdk)
- [JAVA-02. Co przyniosła Java 8 i po co dodano metody domyślne interfejsów?](#java-02-co-przyniosła-java-8-i-po-co-dodano-metody-domyślne-interfejsów)
- [JAVA-03. Dlaczego migrować z Javy 8 do 17 i co rzeczywiście pochodzi z Javy 17?](#java-03-dlaczego-migrować-z-javy-8-do-17-i-co-rzeczywiście-pochodzi-z-javy-17)
- [JAVA-04. Jakie funkcje Javy 21 warto znać i jak mówić o ich użyciu?](#java-04-jakie-funkcje-javy-21-warto-znać-i-jak-mówić-o-ich-użyciu)
- [JAVA-05. Czym record różni się od zwykłej klasy? Czy jest niezmienny?](#java-05-czym-record-różni-się-od-zwykłej-klasy-czy-jest-niezmienny)
- [JAVA-06. Po co sealed classes?](#java-06-po-co-sealed-classes)
- [JAVA-07. Czym różnią się `==`, `equals()` i `hashCode()`?](#java-07-czym-różnią-się--equals-i-hashcode)
- [JAVA-08. Jak działa String Pool i dlaczego String jest niezmienny?](#java-08-jak-działa-string-pool-i-dlaczego-string-jest-niezmienny)
- [JAVA-09. Kiedy String, StringBuilder, a kiedy StringBuffer?](#java-09-kiedy-string-stringbuilder-a-kiedy-stringbuffer)
- [JAVA-10. Jak działają polimorfizm, przeciążanie i ukrywanie metod statycznych?](#java-10-jak-działają-polimorfizm-przeciążanie-i-ukrywanie-metod-statycznych)
- [JAVA-11. Do czego służą `this`, `super` i metody statyczne?](#java-11-do-czego-służą-this-super-i-metody-statyczne)
- [JAVA-12. Czy można przeciążyć `main()`? Którą metodę uruchomi launcher?](#java-12-czy-można-przeciążyć-main-którą-metodę-uruchomi-launcher)
- [JAVA-13. Czym różnią się interfejs funkcyjny i markerowy?](#java-13-czym-różnią-się-interfejs-funkcyjny-i-markerowy)
- [JAVA-14. Czym są lambda i referencja do metody?](#java-14-czym-są-lambda-i-referencja-do-metody)
- [JAVA-15. Czym różnią się kopia płytka i głęboka?](#java-15-czym-różnią-się-kopia-płytka-i-głęboka)
- [JAVA-16. Czym różnią się `final`, `finally`, `finalize()` i `transient`?](#java-16-czym-różnią-się-final-finally-finalize-i-transient)
- [JAVA-17. Czym różnią się Error, Exception, RuntimeException oraz wyjątki checked i unchecked?](#java-17-czym-różnią-się-error-exception-runtimeexception-oraz-wyjątki-checked-i-unchecked)
- [JAVA-18. Czym różnią się `Optional.orElse()` i `orElseGet()`?](#java-18-czym-różnią-się-optionalorelse-i-orelseget)
- [JAVA-19. Jak wyjaśnić SOLID na przykładzie projektu?](#java-19-jak-wyjaśnić-solid-na-przykładzie-projektu)
- [JAVA-20. Kiedy użyć Strategy, Factory albo Observer i jak zachować rozszerzalność?](#java-20-kiedy-użyć-strategy-factory-albo-observer-i-jak-zachować-rozszerzalność)
<!-- question-toc:end -->

## JAVA-01. Czym różnią się JVM, JRE i JDK?

JVM wykonuje bytecode i zapewnia m.in. ładowanie klas, JIT oraz GC. JRE oznacza środowisko uruchomieniowe: JVM i biblioteki. JDK dodaje narzędzia programisty, np. kompilator i diagnostykę. Współczesna dystrybucja nie musi oferować osobnego produktu JRE; runtime można też zbudować przez `jlink`.

W projekcie rozdzielam wersję kompilacji, docelowy poziom bytecode i rzeczywisty runtime. Kod skompilowany dla nowszej Javy nie uruchomi się automatycznie na starszej JVM.

Dla przykładu build może działać na JDK 23, a artefakt być kierowany do Javy 21. Samo ustawienie wersji składni nie wystarcza, jeśli kod używa nowszej biblioteki standardowej: kompilacja powinna kontrolować również dostępne API.

<!-- example:JAVA-01-1 kind:shell-command -->
**Kontekst:** Przykładowe polecenia — PID, ścieżki i środowisko należy dopasować; skrypt ich nie wykonuje.

~~~shell
javac --release 21 Example.java
java -version
~~~

W środowisku produkcyjnym porównuję runtime w obrazie kontenera z runtime CI. UnsupportedClassVersionError wskazuje niezgodność bytecode, natomiast brak klasy może wynikać z classpath lub modułów. Osobno sprawdzam obecność narzędzi diagnostycznych w obrazie: mały runtime może nie zawierać javac ani jcmd. To wpływa na sposób badania incydentu, chociaż aplikacja nadal się uruchamia.

## JAVA-02. Co przyniosła Java 8 i po co dodano metody domyślne interfejsów?

Najważniejsze elementy to lambdy, interfejsy funkcyjne, Stream API, `Optional`, `CompletableFuture` i `java.time`. Metody `default` umożliwiły dodawanie zachowania do interfejsów bez wymuszania implementacji w każdej istniejącej klasie; konflikty dziedziczenia nadal wymagają rozstrzygnięcia.

Lambdy upraszczają przekazywanie zachowania, ale nie oznaczają automatycznej równoległości. W projekcie używam ich do krótkich strategii i transformacji, a czas modeluję typami `java.time`, zamiast mieszać moment zdarzenia z lokalną datą.

Metodę domyślną dobrze ilustruje rozszerzenie istniejącego kontraktu o zachowanie wyrażone przez starszą metodę:

<!-- example:JAVA-02-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
interface PricePolicy {
    long priceInCents(long base);
    default boolean isFree(long base) {
        return priceInCents(base) == 0;
    }
}
~~~

Istniejące implementacje nadal dostarczają podstawową wycenę. Jeśli jednak nowa metoda domyślna zmienia semantykę, zgodność binarna nie chroni przed błędem biznesowym. Dwa niezależne interfejsy z kolidującymi metodami default mogą wymagać jawnego nadpisania. W projektowaniu API pytam więc także, czy rozszerzenie nie dodaje ukrytego I/O, kosztownego obliczenia lub nowego wyjątku do wcześniej prostego kontraktu.

## JAVA-03. Dlaczego migrować z Javy 8 do 17 i co rzeczywiście pochodzi z Javy 17?

Migracja daje dostęp do utrzymywanych dystrybucji, nowszego runtime i zmian od Javy 8: m.in. modułów, `var`, switch expressions, text blocks, records, pattern matching dla `instanceof` i sealed classes. Records ustabilizowano w 16, sealed classes w 17; nie każda funkcja dostępna w 17 została wprowadzona właśnie wtedy.

Sprawdzam zależności, usunięte moduły, refleksję i silniejszą enkapsulację JDK, konfigurację GC oraz wyniki testów i pomiarów. Aktualizacja sama nie gwarantuje przyspieszenia ani bezpieczeństwa: liczą się także poprawki konkretnej dystrybucji i utrzymywane biblioteki.

Migrację rozkładam na trzy kroki: uruchomienie istniejącego artefaktu na nowym runtime, kompilację nowym toolchainem oraz świadome użycie nowych funkcji języka. Dzięki temu łatwiej wskazać, czy regresję spowodował JDK, kompilator, biblioteka czy refaktoryzacja.

Przykład problemu: starsza biblioteka refleksyjnie odczytuje prywatne pole JDK i przestaje działać przez silniejszą enkapsulację. Flaga otwierająca pakiet może być czasowym obejściem, ale docelowo aktualizuję bibliotekę i testuję ścieżkę. Porównuję także TLS, kodowanie, locale i zachowanie GC na rzeczywistych danych. Przyspieszenie mierzę po rozgrzaniu JVM, a nie wyłącznie czasem pierwszego requestu po starcie.

## JAVA-04. Jakie funkcje Javy 21 warto znać i jak mówić o ich użyciu?

W Javie 21 finalne są m.in. virtual threads, record patterns, pattern matching dla `switch` i sequenced collections. Mogę użyć record patterns do rozpakowywania wartości, a virtual threads do licznych blokujących operacji I/O. Nie przypisuję im automatycznej poprawy każdego workloadu.

Na rozmowie wskazuję funkcję, konkretny problem i wynik. Structured concurrency i scoped values miały w Javie 21 status preview, więc nie przedstawiam ich jako stabilnego API tej wersji. [Lista funkcji JDK 21](https://openjdk.org/projects/jdk/21/).

Pattern matching może zamienić ręczne rzutowania i osobne pobieranie pól w jawny podział przypadków. Przykład zgodny z Javą 21:

<!-- example:JAVA-04-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
record Point(int x, int y) {}
static int manhattan(Object value) {
    return switch (value) {
        case Point(int x, int y) -> Math.abs(x) + Math.abs(y);
        default -> throw new IllegalArgumentException("Oczekiwano punktu");
    };
}
~~~

Ten przykład zakłada niewielkie współrzędne oraz niepustą referencję; pełny kontrakt musiałby określić null i przepełnienie. Nowa składnia poprawia czytelność dopiero wtedy, gdy odpowiada modelowi. Na rozmowie wyjaśniam również, dlaczego nie użyłem konkretnej funkcji: np. virtual threads nie pomogą w ciężkim liczeniu, a kolekcje sekwencyjne nie zastępują indeksu potrzebnego do wyszukiwania.

## JAVA-05. Czym record różni się od zwykłej klasy? Czy jest niezmienny?

Record jest zwięzłym nośnikiem danych: dostaje konstruktor kanoniczny, accessory oraz `equals`, `hashCode` i `toString` oparte na komponentach. Jest finalny, a referencje do komponentów nie mogą być przepisywane. Dobrze pasuje do DTO i value objects.

Niezmienność jest płytka: komponent typu `List` lub tablica nadal może zmieniać zawartość. W konstruktorze potrzebne bywają kopie defensywne, również przy odczycie tablicy. Wygenerowane porównanie komponentu tablicowego nie porównuje automatycznie elementów jak `Arrays.equals`.

Dla listy niezmiennych tekstów można zabezpieczyć record w konstruktorze:

<!-- example:JAVA-05-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
record OrderView(String id, java.util.List<String> items) {
    OrderView {
        java.util.Objects.requireNonNull(id);
        items = java.util.List.copyOf(items);
    }
}
~~~

Klient nie może później dopisać elementu przez wejściową listę ani accessor. List.copyOf odrzuca null i wykonuje kopię strukturalną, lecz nie zamraża obiektów elementów. Gdyby elementem była mutowalna pozycja zamówienia, należałoby skopiować ją albo zamienić na niezmienny DTO. Record nadaje się na snapshot odpowiedzi, ale jego automatyczne equals nie zastępuje decyzji, czy encje domenowe porównujemy po tożsamości, czy pełnym stanie.

<!-- follow-up:JAVA-05 -->
**Sprawdź rozumienie:** Record ma komponent List<MutableItem>. Czy List.copyOf wystarcza do głębokiej niezmienności?

<details>
<summary>Kierunek odpowiedzi</summary>

Nie. Zabezpiecza strukturę listy, lecz nie zmienne elementy. Potrzebna jest kopia elementów, niezmienny DTO albo odrębny kontrakt ownershipu.

</details>

## JAVA-06. Po co sealed classes?

Ograniczają listę bezpośrednich podtypów, dzięki czemu hierarchia opisuje zamknięty zbiór wariantów, np. wynik płatności: zaakceptowana, odrzucona, oczekująca. Kompilator może pomóc w wyczerpującej obsłudze wariantów, zależnie od użytej wersji `switch`.

To kontrola modelu, nie mechanizm bezpieczeństwa danych. Nowy wariant może wymagać zmiany konsumentów i serializacji. Stosuję sealed tam, gdzie właściciel domeny rzeczywiście kontroluje rozszerzenia; nie dla otwartego API pluginów.

Zamknięta hierarchia pomaga przenieść regułę „wynik ma dokładnie jeden z wariantów” do systemu typów:

<!-- example:JAVA-06-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
sealed interface Decision permits Approved, Declined {}
record Approved(String authorizationId) implements Decision {}
record Declined(String reasonCode) implements Decision {}
~~~

Nie trzeba tworzyć obiektu z nullable authorizationId, nullable reason i sprzecznym statusem. Każdy wariant może mieć własne niezmienniki. Rozszerzenie hierarchii nadal wymaga analizy JSON, kontraktów konsumentów i starych danych. Wewnętrzna zamknięta hierarchia jest łatwa do rozwijania wspólnym wydaniem; publiczne API używane przez niezależnych klientów potrzebuje strategii obsługi nieznanych wariantów, nawet jeśli lokalny switch jest wyczerpujący.

## JAVA-07. Czym różnią się `==`, `equals()` i `hashCode()`?

`==` dla referencji sprawdza tożsamość obiektu; `equals` może definiować równość wartości. Równe obiekty muszą mieć jednakowy hash, ale jednakowy hash nie dowodzi równości. `equals` musi być m.in. symetryczne i przechodnie.

W kolekcjach haszujących łamanie kontraktu powoduje pozornie znikające wpisy lub duplikaty. Dla encji z generowanym ID trzeba przemyśleć zachowanie przed i po zapisie; nie generuję bezrefleksyjnie równości ze wszystkich zmiennych pól i relacji JPA.

Rozważ dwa obiekty CustomerId utworzone z tego samego numeru. Powinny być równe jako wartości, mimo że znajdują się pod różnymi adresami. HashMap najpierw zawęża kandydatów hashem, a dopiero potem rozstrzyga równość. Wygenerowanie nowego losowego hashCode przy każdym wywołaniu uniemożliwiłoby stabilne znalezienie wpisu.

Dla pieniędzy istotny jest dodatkowy niuans: BigDecimal.equals uwzględnia skalę, więc 1.0 i 1.00 nie są równe tą metodą, chociaż compareTo daje zero. Value object kwoty powinien jawnie określać normalizację i walutę. Kontrakt równości testuję dla różnych instancji, null, podtypów i wartości granicznych, zamiast sprawdzać tylko porównanie obiektu z nim samym.

## JAVA-08. Jak działa String Pool i dlaczego String jest niezmienny?

Literały i wartości internowane mogą współdzielić kanoniczną instancję. Nie każdy `new String` trafia jako nowy wpis do puli; `intern()` zwraca reprezentację kanoniczną. Tekst porównuję przez `equals`, ponieważ optymalizacja puli nie zmienia kontraktu `==`.

Niezmienność umożliwia bezpieczne współdzielenie i stabilny hash, przydatny np. dla kluczy map. Nie oznacza szyfrowania ani łatwego usuwania sekretów z pamięci; hasło jako String może pozostać osiągalne dłużej niż potrzeba.

Pula nie zwalnia z poprawnego porównywania wartości:

<!-- example:JAVA-08-1 kind:java-statements -->
**Kontekst:** Java 21 — instrukcje ciała metody; kompilowane przez skrypt w metodzie pomocniczej.

~~~java
String literal = "order";
String created = new String("order");
boolean sameReference = literal == created;       // false
boolean sameValue = literal.equals(created);     // true
boolean pooledReference = literal == created.intern(); // true
~~~

Internowanie może ograniczać powtórzone reprezentacje przy kontrolowanym słowniku, ale dla dowolnych identyfikatorów żądań dodaje koszt i presję na pamięć bez dużego zysku. Najpierw mierzę duplikację i czas życia tekstów. Niezmienność String chroni treść współdzielonej instancji, a nie pole, które na nią wskazuje: dwa wątki nadal mogą niepoprawnie aktualizować tę samą zmienną typu String.

## JAVA-09. Kiedy String, StringBuilder, a kiedy StringBuffer?

String reprezentuje niezmienny tekst. `StringBuilder` służy do budowania tekstu lokalnie, np. w pętli, bez tworzenia kolejnych pełnych wersji wyniku. `StringBuffer` synchronizuje pojedyncze metody, co kosztuje i nie zapewnia atomowości sekwencji kilku wywołań.

Najprościej ograniczyć builder do jednego zadania i przekazać gotowy String. Współdzielony `StringBuilder` wymaga zewnętrznej ochrony. Konkatenacja w pojedynczym wyrażeniu bywa optymalizowana przez kompilator/runtime; nie zastępuję jej mechanicznie builderem.

W pętli budującej duży raport kolejne konkatenacje mogą wielokrotnie kopiować rosnący prefiks. Builder pozwala gromadzić wynik w powiększanym buforze, choć duża rezerwacja wstępna też kosztuje pamięć. Dla małego komunikatu lepsza jest zwykła czytelna konkatenacja niż „optymalizacja” bez pomiaru.

Synchronizowane metody StringBuffer nie wystarczą do warunku: „jeżeli bufor pusty, dodaj nagłówek, potem treść”. Inny wątek może wejść między wywołaniami. Przy współdzielonym raporcie synchronizuję całą logiczną operację albo zbieram niezależne fragmenty i łączę je jednym właścicielem. Test sprawdza wtedy poprawność kompletnych rekordów, a nie tylko brak wyjątku.

## JAVA-10. Jak działają polimorfizm, przeciążanie i ukrywanie metod statycznych?

Przeciążenie wybierane jest na podstawie typów argumentów znanych kompilatorowi. Nadpisana metoda instancji jest wywoływana zgodnie z rzeczywistym typem obiektu. Metoda statyczna jest ukrywana, a nie dynamicznie nadpisywana: wybór zależy od typu/klasy użytej w wyrażeniu.

Bez konkretnego fragmentu kodu nie da się uczciwie przewidzieć jego wydruku. Przy analizie ustalam deklarowane typy, zestaw przeciążeń, a dopiero potem dispatch. Statyczne metody wywołuję przez nazwę klasy, żeby nie sugerować polimorfizmu.

Minimalny przykład ukazuje różnicę między dispatch statycznym a dynamicznym:

<!-- example:JAVA-10-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
static class Parent {
    static String label() { return "parent"; }
    String kind() { return "parent"; }
}
static class Child extends Parent {
    static String label() { return "child"; }
    @Override String kind() { return "child"; }
}
// Parent value = new Child();
// value.label() -> "parent"; value.kind() -> "child"
~~~

Przeciążenie jest dodatkową decyzją kompilatora: jeśli metoda przyjmuje Object i String, argument deklarowany jako Object wybierze wariant Object nawet wtedy, gdy przechowuje tekst. Rozpisuję więc osobno wybór sygnatury i implementacji. To samo rozumowanie pomaga analizować mocki, callbacki i API z szerokimi przeciążeniami, bez zgadywania wyniku na podstawie samego new Child().

## JAVA-11. Do czego służą `this`, `super` i metody statyczne?

`this` wskazuje bieżącą instancję, `super` pozwala odwołać się do zachowania lub konstruktora nadklasy. Metoda statyczna należy do klasy, nie ma bieżącego `this` i nadaje się np. na fabrykę lub czystą funkcję pomocniczą.

Statyczny modyfikowalny stan jest jednak współdzielony przez wątki korzystające z tej klasy. Nie używam statycznego pola jako schowka na aktualnego użytkownika. Dziedziczenie stosuję dla relacji typu „jest”, a wymienne zachowanie często modeluję kompozycją.

W konstruktorze this może odróżnić pole od parametru, a super wybrać konstruktor lub zachowanie nadklasy. Nie należy jednak traktować this jako „obiektu już gotowego”: rejestracja go w globalnym listenerze podczas konstrukcji pozwala zobaczyć stan częściowy.

Dla przykładu statyczna fabryka Money.ofMinorUnits może kontrolować walutę i utworzyć poprawny value object bez potrzeby instancji fabryki. Statyczny currentCustomer działa zupełnie inaczej: reprezentuje wspólną komórkę dla wszystkich żądań i grozi mieszaniem danych. Nazwa static mówi o związku z klasą, nie o czystości funkcji, braku efektów ubocznych ani bezpieczeństwie wątkowym.

## JAVA-12. Czy można przeciążyć `main()`? Którą metodę uruchomi launcher?

Tak, `main` można przeciążać tak jak inne metody. W klasycznym modelu Javy 8/17/21 launcher szuka `public static void main(String[] args)`; `String...` daje równoważną sygnaturę. Inne przeciążenia wykonają się dopiero po jawnym wywołaniu.

Nie mylę tej reguły z późniejszymi uproszczeniami uruchamiania programów. Przy analizie zadania ustalam wersję języka i opcje preview. Niepoprawny punkt wejścia jest problemem uruchomienia, a nie dowodem, że przeciążanie jest zabronione.

W klasycznym programie launcher wybierze metodę z tablicą argumentów:

<!-- example:JAVA-12-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
public static void main(String[] args) {
    main(3); // jawne wywołanie przeciążenia
}
static void main(int retries) {
    System.out.println(retries);
}
~~~

Tablica args zawiera argumenty programu, nie argumenty JVM takie jak -Xmx. W narzędziu CLI osobno waliduję te argumenty i określam exit code, bo poprawny punkt wejścia nie zapewnia poprawnego zakończenia. Przykład dotyczy klasycznego modelu bez funkcji preview; przy pytaniu o nowsze uproszczone uruchamianie najpierw ustalam wersję JDK. To zapobiega mieszaniu różnych reguł w jednej odpowiedzi.

## JAVA-13. Czym różnią się interfejs funkcyjny i markerowy?

Interfejs funkcyjny ma jeden abstrakcyjny kontrakt metody, więc może być celem lambdy, np. `Predicate`. Metody `default` i statyczne nie powiększają tego kontraktu. Interfejs markerowy, np. `Serializable`, oznacza kategorię typu bez dodawania metody.

`@FunctionalInterface` pozwala kompilatorowi pilnować zamiaru autora. Marker jest użyteczny, gdy klasyfikacja ma działać w systemie typów; adnotacja częściej nadaje się do metadanych odczytywanych przez narzędzia. Żaden wariant sam nie implementuje zachowania.

Interfejs funkcyjny może wyrażać domenowy kontrakt zamiast ogólnego Function:

<!-- example:JAVA-13-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
@FunctionalInterface
interface Discount {
    long apply(long priceInCents);
}
Discount none = price -> price;
~~~

Liczba abstrakcyjnych metod jest oceniana z uwzględnieniem dziedziczenia i reguł metod Object; samo policzenie wszystkich deklaracji w pliku bywa mylące. Marker używany jako ograniczenie typu umożliwia z kolei API przyjmujące tylko określoną kategorię obiektów. Przy Serializable warto pamiętać, że oznaczenie nie czyni serializacji bezpieczną dla niezaufanych danych i nie zastępuje kontroli wersji formatu.

## JAVA-14. Czym są lambda i referencja do metody?

Lambda opisuje implementację interfejsu funkcyjnego, a referencja do metody jest skrótem dla już istniejącego zachowania, np. `Person::age`. Przechwycone zmienne lokalne muszą być finalne lub efektywnie finalne; wskazywany przez nie obiekt nadal może być mutowalny.

Samo zapisanie lambdy nie uruchamia pracy w tle. Jeśli lambda modyfikuje współdzieloną listę w równoległym strumieniu, pozostaje problem współbieżności. W projekcie preferuję krótkie funkcje bez efektów ubocznych i jawne granice wykonania.

Różnica między niezmienną referencją a zmiennym obiektem jest widoczna tutaj:

<!-- example:JAVA-14-1 kind:java-statements -->
**Kontekst:** Java 21 — instrukcje ciała metody; kompilowane przez skrypt w metodzie pomocniczej.

~~~java
var visited = new java.util.ArrayList<String>();
java.util.function.Consumer<String> remember = visited::add;
remember.accept("A"); // lista została zmieniona
~~~

Zmienna visited nie została przepisana, ale obiekt już tak. Uruchomienie tego samego callbacka równolegle wymagałoby innej struktury lub ochrony. Dodatkowo lambda może zatrzymać referencję do całego obiektu serwisu i wydłużyć jego życie, jeśli jest przechowywana w globalnym rejestrze. Dlatego analizuję nie tylko krótki zapis, ale też przechwycone dane, lifecycle callbacka i wątek, na którym będzie wykonywany.

## JAVA-15. Czym różnią się kopia płytka i głęboka?

Kopia płytka kopiuje kontener/pola, ale zachowuje referencje do zagnieżdżonych obiektów. Kopia głęboka odtwarza potrzebny fragment grafu obiektów. Wymaga decyzji o cyklach, tożsamości i obiektach, które można bezpiecznie współdzielić.

`new ArrayList<>(lista)` nie kopiuje elementów. Dla snapshotu zlecenia kopiuję zmienne wartości domenowe, zamiast udawać pełną izolację samą kopią listy. Pełne kopiowanie dużego grafu może być drogie; lepsza bywa niezmienność lub dedykowany DTO.

Przykład płytkiej kopii: oryginalna lista zawiera Customer z mutowalnym adresem. Po new ArrayList<>(original) obie listy wskazują na tego samego Customer. Dodanie elementu zmienia tylko jedną listę, lecz zmiana customer.address.city jest widoczna przez obie.

„Głęboka kopia wszystkiego” także nie jest oczywista: wspólny obiekt adresu może być celowym elementem grafu, a połączenia sieciowego nie da się skopiować jak wartości. Dla snapshotu raportowego projektuję więc dedykowaną reprezentację z potrzebnymi polami. Test powinien zmodyfikować dane wejściowe i zagnieżdżone elementy po utworzeniu kopii, a następnie sprawdzić niezmienność snapshotu.

## JAVA-16. Czym różnią się `final`, `finally`, `finalize()` i `transient`?

`final` ogranicza przypisanie, dziedziczenie albo nadpisywanie. `finally` służy do wykonania sprzątania przy wychodzeniu z bloku; nie gwarantuje wykonania po zabiciu procesu. `finalize()` to historyczna, niedeterministyczna finalizacja, wycofywana z Javy. Zasoby zamykam przez `AutoCloseable` i try-with-resources. [JEP 421](https://openjdk.org/jeps/421).

`transient` pomija pole w domyślnej serializacji Java; nie synchronizuje i nie zabezpiecza przed każdym serializerem ani logowaniem. `volatile` dotyczy widoczności i uporządkowania, a nie trwałości danych.

Zwalnianie zasobu powinno wynikać z zakresu operacji:

<!-- example:JAVA-16-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
static long countLines(java.nio.file.Path path) throws java.io.IOException {
    try (var lines = java.nio.file.Files.lines(path)) {
        return lines.count();
    }
}
~~~

Strumień zostanie zamknięty także po błędzie. Gdy jednocześnie zawiedzie operacja i close, mechanizm try-with-resources zachowuje dodatkowy wyjątek jako suppressed, zamiast bezrefleksyjnie zgubić pierwotną przyczynę. Finalna referencja do strumienia nie zamknęłaby go sama. Podobnie transient nie chroni pola hasła przed ręcznym toString, JSON ani logowaniem — każda z tych ścieżek ma osobne reguły.

## JAVA-17. Czym różnią się Error, Exception, RuntimeException oraz wyjątki checked i unchecked?

`Error` zwykle sygnalizuje poważny problem runtime; `Exception` obejmuje błędy, które aplikacja może obsłużyć. Checked exceptions wymagają deklaracji lub przechwycenia; `RuntimeException` i `Error` są unchecked. To podział kontraktu języka, nie prosty ranking ważności.

Własny wyjątek powinien przekazać sens biznesowy i zachować przyczynę. Nie przechwytuję ogólnie `Throwable`, żeby kontynuować po OOM. Na granicy API mapuję znane błędy na stabilny kontrakt i nie wysyłam stack trace do klienta.

Wyjątek domenowy powinien pozwalać podjąć sensowną decyzję, np. „brak dostępnego limitu” różni się od „nie udało się połączyć z operatorem”. Pierwszy może prowadzić do odmowy, drugi do stanu nieznanego i sprawdzenia wyniku. Wspólny catch z odpowiedzią „spróbuj ponownie” może więc wywołać niepoprawny retry.

Łapię wyjątek na warstwie, która umie dodać kontekst lub rozstrzygnąć politykę. Jeśli opakowuję go we własny typ, zachowuję cause. Nie loguję tej samej awarii z pełnym stack trace w każdej warstwie. Przy checked exceptions dodatkowo sprawdzam reguły rollbacku, bo kontrakt kompilatora i kontrakt transakcyjny to osobne mechanizmy.

## JAVA-18. Czym różnią się `Optional.orElse()` i `orElseGet()`?

Argument `orElse` jest obliczany przed wywołaniem metody, nawet gdy wartość istnieje. Supplier w `orElseGet` jest uruchamiany dopiero dla pustego Optional. Różnica ma znaczenie, gdy fallback wykonuje zapytanie, alokuje zasoby albo ma efekt uboczny.

Dla stałej wartości `orElse` jest czytelne; dla kosztownego obliczenia wybieram `orElseGet`. Optional wyraża brak wyniku, ale nie zastępuje walidacji i nie uzasadnia ukrywania wyjątków przez zwracanie pustej wartości.

Różnicę widać nawet bez pustego Optional:

<!-- example:JAVA-18-1 kind:java-sketch -->
**Kontekst:** Szkic Java — wymaga wskazanych pól lub metod otoczenia; poza automatyczną kompilacją.

~~~java
var value = java.util.Optional.of("cached");
String eager = value.orElse(loadDefault());       // loadDefault się wykona
String lazy = value.orElseGet(() -> loadDefault()); // tutaj nie
~~~

To szkic wywołania; loadDefault oznacza domenową operację, np. kosztowny odczyt. Jeśli zapisuje metrykę, wysyła HTTP albo rzuca wyjątek, orElse może uruchomić ten efekt mimo dostępnej wartości. W testach weryfikuję zatem liczbę wywołań fallbacku dla obu przypadków. Supplier nie oznacza wykonania async: w zwykłym orElseGet pracuje na bieżącym wątku, gdy wartość jest pusta.

## JAVA-19. Jak wyjaśnić SOLID na przykładzie projektu?

SOLID pomaga wyznaczyć granice zmiany i zachowania na przykładzie zamówienia:

| Zasada | Decyzja projektowa |
| --- | --- |
| SRP | orkiestracja checkout, kalkulacja ceny i format raportu mają różne powody zmiany |
| OCP | nowa polityka rabatu rozszerza ustalony kontrakt |
| LSP | każdy adapter płatności zachowuje wymagane gwarancje, np. idempotencję |
| ISP | konsument odczytu nie zależy od niepotrzebnych metod administracyjnych |
| DIP | przypadek użycia zależy od portu płatności, a szczegóły operatora są adapterem |

Kontrprzykład dla LSP: PaymentGateway obiecuje pojedynczy efekt dla tego samego klucza, ale jeden adapter go ignoruje. Sygnatura się zgadza, zachowanie łamie kontrakt.

Nie dodaję interfejsu do każdej klasy. Abstrakcja ma pomóc w istniejącym wariancie lub niezależnej odpowiedzialności, inaczej zwiększa koszt nawigacji i testów. OCP nie zabrania edycji kodu; zachęca do stabilnego rozszerzania faktycznie zmiennych miejsc.

<!-- follow-up:JAVA-19 -->
**Sprawdź rozumienie:** Adapter implementuje wszystkie metody PaymentGateway, ale ignoruje idempotency key. Który kontrakt narusza?

<details>
<summary>Kierunek odpowiedzi</summary>

LSP dotyczy zachowania: jeżeli bramka obiecuje jeden efekt dla tej samej operacji, taki adapter nie jest poprawnym zamiennikiem. Kompilacja interfejsu tego nie sprawdzi.

</details>

## JAVA-20. Kiedy użyć Strategy, Factory albo Observer i jak zachować rozszerzalność?

Strategy wymienia sposób wykonania zadania, np. liczenie prowizji. Factory odpowiada za utworzenie właściwego obiektu. Observer powiadamia zainteresowanych o zdarzeniu. Mogą współpracować, ale rozwiązują inne problemy.

Rozszerzalność opieram na stabilnych kontraktach i konkretnych przewidywanych zmianach. Przy Observerze określam synchronię, kolejność, błędy i możliwość powtórzenia powiadomienia. Zdarzenie w pamięci nie daje trwałej dostawy, a dodanie wzorca bez potrzeby nie poprawia projektu.

Załóżmy, że checkout ma kilku operatorów płatności. Factory wybiera lub tworzy adapter na podstawie konfiguracji; Strategy opisuje wymienne wykonanie autoryzacji. Observer może powiadomić o zakończeniu płatności, lecz decyzja „który operator” nadal nie należy do mechanizmu powiadamiania.

Projekt rozszerzenia zaczynam od przykładu drugiej implementacji i testu kontraktowego. Jeśli wspólny interfejs wymaga szeregu metod unsupported, granica jest zapewne źle wybrana. Dla powiadomień ustalam, czy wyjątek odbiorcy blokuje główną operację i czy zdarzenie trzeba odtworzyć po restarcie. Te decyzje częściej wpływają na poprawność niż nazwa zastosowanego wzorca.

<!-- labs:start -->
## Powiązane laboratoria

Poniższe materiały rozwijają wybrane mechanizmy rozdziału. Każde laboratorium ma własne założenia, ograniczenia i komendy weryfikacji.

- [heap vs stack](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/heap_vs_stack/README.md)
- [Stage 1E — refaktoryzacja i bezpieczna zmiana](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_e/README.md)
- [temporal correctness](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/temporal_correctness/README.md)
<!-- labs:end -->
