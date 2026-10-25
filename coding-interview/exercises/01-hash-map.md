# Zadania: hash map

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/01-hash-map.md)

Wszystkie metody poniżej są nierozwiązane. Bloki „szkielet” uzupełniasz, bloki
„testy” uruchamia skrypt dla wskazanego identyfikatora.

## HASH-01 — pierwszy niepowtarzający się znak

**Poziom:** L1
**Wymagania:** [Karta techniki](../patterns/01-hash-map.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Kolejność w wyniku jest kolejnością wejścia.

</details>

<details>
<summary>Wskazówka 2</summary>

Najpierw poznaj liczność w całym napisie.

</details>

<details>
<summary>Wskazówka 3</summary>

Po zliczeniu przejdź napis od lewej i znajdź liczność jeden.

</details>

**Treść i kontrakt.** Dla napisu ASCII zwróć indeks pierwszego znaku występującego
dokładnie raz w całym napisie. Jeśli takiego nie ma, zwróć -1. Spacja i wielkość
liter mają znaczenie. Długość 0..100 000. Przykład: `swiss` → 1 (znak w).
Docelowo O(n) czasu i pamięć zależna od alfabetu.

Kontekst: nierozwiązany szkielet metody.

<!-- exercise:HASH-01 -->
```java
static int firstUniqueIndex(String text) {
    throw new UnsupportedOperationException("TODO HASH-01");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:HASH-01 -->
```java
static void verify() {
    assert firstUniqueIndex("swiss") == 1;
    assert firstUniqueIndex("leetcode") == 0;
    assert firstUniqueIndex("aabbc") == 4;
    assert firstUniqueIndex("aabb") == -1;
    assert firstUniqueIndex("") == -1;
    assert firstUniqueIndex("x") == 0;
    assert firstUniqueIndex("aA") == 0;
    assert firstUniqueIndex(" a ") == 1;
}
```

## HASH-02 — równość multizbiorów liter

**Poziom:** L1
**Wymagania:** [Karta techniki](../patterns/01-hash-map.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Zbiór liter nie zachowuje ich wielokrotności.

</details>

<details>
<summary>Wskazówka 2</summary>

Potrzebujesz liczników liter.

</details>

<details>
<summary>Wskazówka 3</summary>

Porównaj liczniki obu napisów; długości muszą być równe.

</details>

**Treść i kontrakt.** Sprawdź, czy dwa napisy z małych liter a–z mają te same
liczności liter. Kolejność nie ma znaczenia. Długości 0..100 000. Puste napisy
są anagramami. `aab` oraz `abb` nie są anagramami mimo tego samego zbioru liter.
Docelowo O(n + m) czasu, pamięć zależna od alfabetu.

Kontekst: nierozwiązany szkielet metody.

<!-- exercise:HASH-02 -->
```java
static boolean areAnagrams(String first, String second) {
    throw new UnsupportedOperationException("TODO HASH-02");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:HASH-02 -->
```java
static void verify() {
    assert areAnagrams("listen", "silent");
    assert areAnagrams("aab", "aba");
    assert areAnagrams("", "");
    assert areAnagrams("z", "z");
    assert !areAnagrams("aab", "abb");
    assert !areAnagrams("a", "");
    assert !areAnagrams("rat", "car");
    assert !areAnagrams("ab", "aabb");
}
```

## HASH-03 — najdłuższy ciąg kolejnych wartości

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/01-hash-map.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Nie rozwijaj tego samego ciągu od każdej wartości.

</details>

<details>
<summary>Wskazówka 2</summary>

Rozwijaj tylko wartość bez poprzednika w zbiorze.

</details>

<details>
<summary>Wskazówka 3</summary>

Chroń granice int, aby MAX_VALUE nie łączył się z MIN_VALUE.

</details>

**Treść i kontrakt.** W nieposortowanej tablicy int znajdź długość najdłuższego
zbioru kolejnych liczb całkowitych. Ich indeksy nie muszą być kolejne. Duplikaty
nie wydłużają ciągu. Długość 0..100 000; puste wejście → 0. Nie zmieniaj tablicy.
MIN_VALUE i MAX_VALUE nie są sąsiadami. Przykład `[100,4,200,1,3,2]` → 4.
Docelowo oczekiwane O(n) czasu, O(n) pamięci; uzasadnij liczbę odwiedzeń wartości.

Kontekst: nierozwiązany szkielet metody.

<!-- exercise:HASH-03 -->
```java
static int longestConsecutive(int[] values) {
    throw new UnsupportedOperationException("TODO HASH-03");
}
```

Kontekst: wykonywalne testy, również granice int i brak mutacji.

<!-- tests:HASH-03 -->
```java
static void verify() {
    int[] input = {100, 4, 200, 1, 3, 2};
    assert longestConsecutive(input) == 4;
    assert Arrays.equals(input, new int[] {100, 4, 200, 1, 3, 2});
    assert longestConsecutive(new int[] {1, 2, 2, 3}) == 3;
    assert longestConsecutive(new int[] {-2, -1, 0, 2}) == 3;
    assert longestConsecutive(new int[0]) == 0;
    assert longestConsecutive(new int[] {7}) == 1;
    assert longestConsecutive(new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE}) == 1;
    assert longestConsecutive(new int[] {Integer.MAX_VALUE - 1, Integer.MAX_VALUE}) == 2;
    assert longestConsecutive(new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE + 1}) == 2;
}
```
