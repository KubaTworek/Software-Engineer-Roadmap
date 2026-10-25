# Zadania: Trie

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/17-trie.md)

Każde zadanie jest niezależne; metody pozostają TODO. Importy java.util.* zapewnia skrypt.

## TRIE-01 — istnienie prefiksów

**Poziom:** L1
**Wymagania:** [Trie](../patterns/17-trie.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Dla słów a–z i zapytań o prefiksy zwróć boolean[] w kolejności zapytań. Pusty prefiks jest poprawną ścieżką nawet przy pustym słowniku; inne wymagają co najmniej jednego słowa z takim początkiem. Suma długości wejść do 100 000, bez mutacji. Użyj Trie, koszt liniowy w sumie długości.

<details>
<summary>Wskazówka 1</summary>

Czy prefiks musi kończyć się na znaczniku całego słowa?

</details>

<details>
<summary>Wskazówka 2</summary>

Każda litera wybiera jedną krawędź; brak krawędzi kończy zapytanie.

</details>

<details>
<summary>Wskazówka 3</summary>

Pusty prefiks oznacza samo dojście do korzenia, nie sprawdzenie terminal.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:TRIE-01 -->
```java
static boolean[] prefixQueries(String[] words, String[] prefixes) {
    throw new UnsupportedOperationException("TODO TRIE-01");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:TRIE-01 -->
```java
static void verify() {
    assert Arrays.equals(prefixQueries(new String[] {"kot","kotek","pies"}, new String[] {"ko","kot","kota","","pie"}), new boolean[] {true,true,false,true,true});
    assert Arrays.equals(prefixQueries(new String[0], new String[] {"","a"}), new boolean[] {true,false});
    assert prefixQueries(new String[] {""}, new String[0]).length == 0;
}
```

## TRIE-02 — liczba różnych słów z prefiksem

**Poziom:** L2
**Wymagania:** [Trie](../patterns/17-trie.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Dla słów i prefiksów a–z zwróć liczbę różnych słów zaczynających się każdym prefiksem. Powtórzone słowo licz raz. Puste słowo jest dozwolone; pusty prefiks liczy cały zbiór, także puste słowo. Suma długości oraz liczba elementów do 100 000, bez mutacji. Koszt liniowy w rozmiarze wejścia przy stałym alfabecie.

<details>
<summary>Wskazówka 1</summary>

Duplikat dodania nie powinien zwiększyć żadnego licznika.

</details>

<details>
<summary>Wskazówka 2</summary>

Najpierw rozstrzygnij, czy całe słowo jest nowe; potem zaktualizuj jego ścieżkę.

</details>

<details>
<summary>Wskazówka 3</summary>

Licznik korzenia obejmuje wszystkie różne słowa, terminal korzenia reprezentuje puste słowo.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:TRIE-02 -->
```java
static int[] distinctPrefixCounts(String[] words, String[] prefixes) {
    throw new UnsupportedOperationException("TODO TRIE-02");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:TRIE-02 -->
```java
static void verify() {
    assert Arrays.equals(distinctPrefixCounts(new String[] {"a","ab","ab","abc",""}, new String[] {"a","ab","","x"}), new int[] {3,2,4,0});
    assert Arrays.equals(distinctPrefixCounts(new String[0], new String[] {""}), new int[] {0});
    assert Arrays.equals(distinctPrefixCounts(new String[] {"",""}, new String[] {""}), new int[] {1});
}
```

## TRIE-03 — najdłuższe słowo budowane literami

**Poziom:** L3
**Wymagania:** [Trie](../patterns/17-trie.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Wybierz najdłuższe słowo, którego każdy niepusty prefiks również znajduje się w słowniku. Remis rozstrzygaj najmniejszym leksykograficznie słowem. Brak kandydata daje pusty napis. Litery a–z, duplikaty dozwolone, suma długości do 100 000, bez mutacji. Docelowo liniowy koszt przejścia po Trie plus koszt zapisania wyniku.

<details>
<summary>Wskazówka 1</summary>

Istnienie ścieżki nie wystarcza: każdy odwiedzony prefiks ma być słowem.

</details>

<details>
<summary>Wskazówka 2</summary>

Rozwijaj tylko węzły terminalne, zaczynając od dzieci korzenia.

</details>

<details>
<summary>Wskazówka 3</summary>

Porównuj najpierw długość, potem leksykograficznie; nie kopiuj całych ścieżek w każdym kroku bez analizy kosztu.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:TRIE-03 -->
```java
static String longestBuildable(String[] words) {
    throw new UnsupportedOperationException("TODO TRIE-03");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:TRIE-03 -->
```java
static void verify() {
    assert longestBuildable(new String[] {"w","wo","wor","worl","world","banana"}).equals("world");
    assert longestBuildable(new String[] {"a","ap","app","appl","apple","apply"}).equals("apple");
    assert longestBuildable(new String[] {"ab","abc"}).equals("");
    assert longestBuildable(new String[0]).equals("");
    assert longestBuildable(new String[] {"a","ab","ab",""}).equals("ab");
}
```
