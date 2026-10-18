# Standard redakcyjny laboratoriów

## Po co istnieje ten standard

`backend-engineering` jest kompendium, a nie jedną aplikacją. Czytelnik powinien
w ciągu kilkunastu sekund rozpoznać, czego uczy dany katalog, jaki błąd pokazuje,
który test stanowi dowód oraz gdzie kończy się gwarancja przykładu. Dlatego każde
README laboratorium zaczyna się od redagowanej karty materiału, a szczegółowa teoria
pozostaje poniżej.

## Docelowy porządek laboratorium

Materiał powinien odpowiadać — jawnie albo przez równoważne, bardziej naturalne
nagłówki — na sześć pytań:

1. **Problem:** jaka awaria, koszt albo niejednoznaczność uzasadnia laboratorium?
2. **Niezmiennik:** co musi pozostać prawdą niezależnie od danych i przeplotu?
3. **Naiwny przykład:** jakie kuszące rozwiązanie łamie niezmiennik?
4. **Poprawne rozwiązanie:** jaki mechanizm chroni niezmiennik i na jakiej granicy?
5. **Test:** która obserwacja dowodzi gwarancji albo reprodukuje kontrprzykład?
6. **Ograniczenia produkcyjne:** czego model, fake, H2 lub pojedyncza JVM nie dowodzi?

Nie każdy dokument potrzebuje sześciu nagłówków o identycznych nazwach. README
nadrzędne może być mapą, a materiał czysto diagnostyczny nie musi wymyślać
„naiwnego” kodu. Obowiązkowa karta na początku zachowuje jednak wspólny kontrakt.

## Oznaczenia zakresu

| Oznaczenie | Znaczenie |
| --- | --- |
| `fundament` | mechanizm języka, JVM, Springa, danych albo sieci potrzebny niezależnie od architektury |
| `praktyka-produkcyjna` | granice komponentów, kontrakty, wdrożenie, bezpieczeństwo i obsługa awarii |
| `temat-zaawansowany` | zachowanie systemu pod skalą, koordynacja, obserwowalność i cloud |

Etapy 1–3 otrzymują odpowiednio te trzy oznaczenia. Każde laboratorium dziedziczy
zakres etapu, w którym rozwija dany problem.

## Role klas edukacyjnych

Rola opisuje intencję przykładu, a nie ocenę jakości kodu:

| Rola | Znaczenie | Typowe nazwy |
| --- | --- | --- |
| `naive` | celowy kontrprzykład łamiący gwarancję albo ukrywający koszt | `Broken*`, `Naive*`, `Unsafe*`, `Legacy*` |
| `correct` | minimalne rozwiązanie chroniące niezmiennik w granicy laboratorium | `Safe*`, `Correct*`, `Fenced*`, `Idempotent*`, `Atomic*`, `Validated*` |
| `simulation` | deterministyczny model, fake lub implementacja pamięciowa do badania zachowania | `*Simulation`, `*Simulator`, `InMemory*`, `Fake*`, `*Demo` |
| `production-boundary` | port lub adapter pokazujący miejsce integracji z prawdziwą infrastrukturą | `*Controller`, `*Adapter`, `*Publisher`, `*Consumer`, `*Configuration` |

Klasa może mieć więcej niż jedną rolę, np. `IdempotentCounterStore` jest poprawnym
rozwiązaniem wewnątrz symulacji. Brak etykiety nie oznacza automatycznie kodu
produkcyjnego. Nazwy `Broken`, `Naive` i `Unsafe` są dozwolone wyłącznie dla
świadomych kontrprzykładów opisanych w README i objętych testem demonstrującym błąd.

## Zasady testów dydaktycznych

- Nazwa testu opisuje obserwowalne zachowanie albo niezmiennik, nie nazwę metody.
- Test pozytywny pokazuje gwarancję; test negatywny pokazuje jej granicę lub
  kontrprzykład.
- Nie testujemy prywatnej implementacji, jeśli istotny jest kontrakt publiczny.
- Test z fake'em dowodzi logiki modelu, a nie zachowania PostgreSQL, Redis czy Kafki.
- Benchmark ma osobny test poprawności danych i nie przechowuje „magicznych”
  wyników wydajnościowych.
- Testy infrastrukturalne są jawnie oznaczone i uruchamiane oddzielnym profilem.

## Utrzymanie kart

Skrypt [`scripts/update-material-cards.ps1`](scripts/update-material-cards.ps1)
aktualizuje komendę i katalog uruchomienia oraz zachowuje ręcznie opracowane pola
`Uczy`, `Typowy błąd`, `Role klas` i `Granica`. Nie przepisuje treści dokumentu.
Katalog `material-topics.psd1` dostarcza konkretne punkty dydaktyczne przy tworzeniu
nowej karty; nie zastępuje oceny kodu. Role sugerowane z nazwy klasy wymagają
sprawdzenia: nazwa `Safe` sama nie stanowi dowodu poprawności.

Komendy kart wykonuje się z `backend-engineering`. Samodzielne aplikacje Stage 2C
i Stage 3C wskazują własny `pom.xml` przez `-f`. Test `EditorialDocumentationTest`
chroni strukturę karty, a repozytoryjny
[`check-documentation.ps1`](../scripts/check-documentation.ps1) sprawdza lokalne
linki i nagłówki, istnienie wskazanych testów i modułów, mapę tematów oraz zgodność
profili infrastrukturalnych z CI. Kontrola nie odpytuje zewnętrznych stron i nie
ocenia automatycznie poprawności merytorycznej teorii.
