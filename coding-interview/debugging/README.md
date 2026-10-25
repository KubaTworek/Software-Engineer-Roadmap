# Diagnozowanie błędów

[Mapa modułu](../README.md) · [Osiem ćwiczeń](01-exercises.md)

Tutaj dostajesz kod, który się kompiluje, oraz testy jego wymaganego zachowania.
Każda początkowa implementacja ma błąd logiczny. W odróżnieniu od banku TODO
uruchomienie początkowego zadania daje FAIL. Naprawę zapisujesz w tym samym
bloku Markdown. Ten katalog nie jest automatycznie dodawany do zwykłego banku.

Przed edycją zapisz najmniejszy kontrprzykład, stan algorytmu przed błędnym krokiem
i naruszony niezmiennik. Po edycji dopisz test regresyjny i wyjaśnij, dlaczego
zmiana obejmuje całą klasę błędów, a nie tylko pojedynczy przykład.

Z katalogu coding-interview, PowerShell 7 oraz JDK 21+:

```powershell
# Tylko kompilacja wszystkich ośmiu ćwiczeń.
pwsh -File scripts/check-exercises.ps1 -PracticeFile debugging/01-exercises.md

# Testy konkretnej naprawy: początkowo oczekiwany FAIL, po poprawce PASS.
pwsh -File scripts/check-exercises.ps1 -PracticeFile debugging/01-exercises.md -Exercise BUG-01
```

Raport pokazuje oryginalny plik Markdown i linię, nawet jeśli kompilator albo JVM
zwróciły ścieżkę tymczasowego pliku Java. Można podać -JdkHome jak w innych kontrolach.
W verification/ znajdują się rozwiązania kontrolne i statyczne kopie początkowych
błędów używane do walidacji testów. Nie otwieraj ich przed własną analizą.

## Notatka po naprawie

Poniższy blok jest szablonem opisowym, nie kodem wykonywanym.

```text
Identyfikator:
Najmniejsze wejście i wynik oczekiwany / otrzymany:
Stan bezpośrednio przed błędem:
Naruszony niezmiennik lub warunek kontraktu:
Dlaczego poprawka działa dla wszystkich dopuszczalnych wejść:
Nowy test regresyjny:
Czas i pamięć po poprawce:
```
