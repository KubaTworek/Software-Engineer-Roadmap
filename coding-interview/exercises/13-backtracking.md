# Zadania: backtracking

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/13-backtracking.md)

Wyniki muszą być kompletne i bez powtórzeń. Kolejność różnych wyników nie jest
narzucona. Listy reprezentujące różne wyniki nie mogą współdzielić modyfikowalnego
bufora. Testy nie wymagają, aby zwracane kolekcje były modyfikowalne.

## BACK-01 — kombinacje k z n

**Poziom:** L1
**Wymagania:** [Karta techniki](../patterns/13-backtracking.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Ta sama kombinacja nie może powstać w różnych kolejnościach.

</details>

<details>
<summary>Wskazówka 2</summary>

Kolejny numer wybieraj większy od poprzedniego.

</details>

<details>
<summary>Wskazówka 3</summary>

Zapisuj kopię przy długości k; odetnij brak wystarczającej liczby kandydatów.

</details>

**Treść i kontrakt.** Zwróć wszystkie k-elementowe podzbiory zbioru 1..n.
Każda kombinacja ma mieć liczby w kolejności rosnącej. `0 <= n,k <= 10`;
k > n → pusta lista; k = 0 → lista z jedną pustą kombinacją.
Docelowo koszt zależny od enumerowanych gałęzi i rozmiaru wyniku; podaj go
w notatce, uwzględniając kopiowanie kombinacji i pamięć stosu.

Kontekst: nierozwiązany szkielet.

<!-- exercise:BACK-01 -->
```java
static List<List<Integer>> combinations(int n, int k) {
    throw new UnsupportedOperationException("TODO BACK-01");
}
```

Kontekst: wykonywalne testy kompletności i unikalności.

<!-- tests:BACK-01 -->
```java
static void verify() {
    List<List<Integer>> result = combinations(4, 2);
    assert result.size() == 6;
    assert new HashSet<>(result).equals(Set.of(List.of(1, 2), List.of(1, 3), List.of(1, 4),
            List.of(2, 3), List.of(2, 4), List.of(3, 4)));
    assert combinations(3, 3).equals(List.of(List.of(1, 2, 3)));
    assert combinations(3, 0).equals(List.of(List.of()));
    assert combinations(0, 0).equals(List.of(List.of()));
    assert combinations(2, 3).isEmpty();
    List<List<Integer>> singles = combinations(3, 1);
    assert singles.size() == 3;
    assert new HashSet<>(singles).equals(Set.of(List.of(1), List.of(2), List.of(3)));
    assert new HashSet<>(result).size() == 6;
}
```

## BACK-02 — unikalne permutacje multizbioru

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/13-backtracking.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Równe wartości na tym samym poziomie mogą tworzyć identyczny wynik.

</details>

<details>
<summary>Wskazówka 2</summary>

Potrzebujesz użytych egzemplarzy i reguły pomijania duplikatów.

</details>

<details>
<summary>Wskazówka 3</summary>

Po sortowaniu kopii odróżniaj duplikat użyty w prefiksie od pominiętego na tym poziomie.

</details>

**Treść i kontrakt.** Zwróć wszystkie różne permutacje tablicy int z możliwymi
duplikatami. Użyj każdego elementu dokładnie raz. n w 0..8; puste wejście ma
jedną permutację: pustą listę. Nie zmieniaj wejścia.
`[1,1,2]` daje dokładnie trzy wyniki. Koszt ma obejmować zapis pełnych permutacji;
uzasadnij, że nie generujesz tej samej permutacji wiele razy.

Kontekst: nierozwiązany szkielet.

<!-- exercise:BACK-02 -->
```java
static List<List<Integer>> uniquePermutations(int[] values) {
    throw new UnsupportedOperationException("TODO BACK-02");
}
```

Kontekst: wykonywalne testy.

<!-- tests:BACK-02 -->
```java
static void verify() {
    int[] input = {2, 1, 1};
    List<List<Integer>> result = uniquePermutations(input);
    assert result.size() == 3;
    assert new HashSet<>(result).equals(Set.of(List.of(1, 1, 2), List.of(1, 2, 1), List.of(2, 1, 1)));
    assert Arrays.equals(input, new int[] {2, 1, 1});
    assert uniquePermutations(new int[0]).equals(List.of(List.of()));
    assert uniquePermutations(new int[] {7, 7}).equals(List.of(List.of(7, 7)));
    List<List<Integer>> signed = uniquePermutations(new int[] {-1, 0});
    assert signed.size() == 2;
    assert new HashSet<>(signed).equals(Set.of(List.of(-1, 0), List.of(0, -1)));
    List<List<Integer>> distinct = uniquePermutations(new int[] {1, 2, 3});
    assert distinct.size() == 6 && new HashSet<>(distinct).size() == 6;
    for (List<Integer> permutation : distinct) {
        assert permutation.size() == 3;
        assert new HashSet<>(permutation).equals(Set.of(1, 2, 3));
    }
}
```

## BACK-03 — wszystkie poprawne nawiasowania

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/13-backtracking.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Poprawność da się sprawdzać na prefiksie.

</details>

<details>
<summary>Wskazówka 2</summary>

Śledź otwarcia i zamknięcia.

</details>

<details>
<summary>Wskazówka 3</summary>

Dodaj otwarcie do limitu par, zamknięcie tylko przy istniejącym niedomkniętym otwarciu.

</details>

**Treść i kontrakt.** Dla `0 <= pairs <= 8` zwróć wszystkie poprawne napisy
złożone z dokładnie pairs par nawiasów okrągłych. Poprawny napis nigdy nie ma
więcej zamknięć niż otwarć w prefiksie, a końcowy bilans wynosi zero.
Dla 0 wynik zawiera pusty napis. Uzasadnij odcinanie niedopuszczalnych gałęzi
i koszt zależny od liczby oraz długości wyników.

Kontekst: nierozwiązany szkielet.

<!-- exercise:BACK-03 -->
```java
static List<String> parentheses(int pairs) {
    throw new UnsupportedOperationException("TODO BACK-03");
}
```

Kontekst: wykonywalne testy, w tym niezależna walidacja wyników dla 4 par.

<!-- tests:BACK-03 -->
```java
static void verify() {
    assert parentheses(0).equals(List.of(""));
    assert parentheses(1).equals(List.of("()"));
    List<String> two = parentheses(2);
    assert two.size() == 2 && new HashSet<>(two).equals(Set.of("(())", "()()"));
    List<String> three = parentheses(3);
    assert three.size() == 5;
    assert new HashSet<>(three).equals(Set.of("((()))", "(()())", "(())()", "()(())", "()()()"));
    List<String> four = parentheses(4);
    assert four.size() == 14 && new HashSet<>(four).size() == 14;
    for (String text : four) {
        assert text.length() == 8;
        int balance = 0;
        for (char symbol : text.toCharArray()) {
            assert symbol == '(' || symbol == ')';
            balance += symbol == '(' ? 1 : -1;
            assert balance >= 0;
        }
        assert balance == 0;
    }
}
```
