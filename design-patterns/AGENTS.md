# Zasady modułu design-patterns

To kompendium dokumentacyjne. Cała wiedza i kod przykładowy znajdują się w Markdown. Czytaj README.md,
MATERIALY.md i dokument najbliższej kategorii przed zmianą.

- Nie dodawaj źródeł Java, plików klas, wrapperów ani buildu aplikacji.
- Zachowuj 252 karty, komplet 23 GoF, indeks i lokalne kotwice.
- Każda karta wyjaśnia intuicję, problem, role, mechanizm w kodzie, konkretny przypadek użycia z danymi,
  przebiegiem i wynikiem, koszt, awarię oraz ćwiczenie.
- Opis ma być zrozumiały bez otwierania zewnętrznej klasy. Potrzebne typy objaśniaj przy fragmencie; dłuższe
  modele również umieszczaj w MD.
- Kod ma cztery spacje wcięcia, czytelne kroki i polskie komentarze wyjaśniające niezmiennik, własność stanu
  oraz powód kolejności operacji.
- Każdy blok ma kontekst: fragment modelu, szkic wymagający otoczenia, szablon SQL, kontrakt HTTP, pseudokod
  albo scenariusz asercji.
- Nie nazywaj materiału automatycznie przetestowanym lub produkcyjnym, jeśli kontrola dokumentacji nie
  wykonuje danego fragmentu.
- Rozróżniaj wzorzec, styl, idiom i algorytm. Wyjaśniaj różnice wariantów.
- Przy granicach rozproszonych określ źródło prawdy, trwałość, idempotencję, timeout, retry, kolejność i
  reakcję na częściowy sukces.
- Studia w labs/ i dłuższe przykłady w examples/ są dokumentami, nie projektami.
- Weryfikacja z modułu: `pwsh -File scripts/check-catalog.ps1`. Z korzenia:
  `pwsh -File scripts/check-documentation.ps1` i `git diff --check`. Moduł nie ma testów ani buildu aplikacji;
  sprawdzanie linków i struktury nie zastępuje prześledzenia poprawności kodu i założeń scenariusza.
