# Zadania: sliding window

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/03-sliding-window.md)

## WIN-01 — największa suma dokładnie k elementów

**Poziom:** L1
**Wymagania:** [Karta techniki](../patterns/03-sliding-window.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Kolejne okna różnią się dwoma elementami.

</details>

<details>
<summary>Wskazówka 2</summary>

Odejmij opuszczający element i dodaj nowy.

</details>

<details>
<summary>Wskazówka 3</summary>

Zainicjalizuj maksimum pierwszym pełnym oknem, nie zerem.

</details>

**Treść i kontrakt.** Zwróć największą sumę spójnego fragmentu o długości dokładnie
k w tablicy int. Długość 1..100 000, `1 <= k <= n`, wynik long. Wartości mogą być
ujemne; nie można zamiast wyniku wybrać pustego fragmentu. Bez mutacji wejścia.
`[-5,-2,-7]`, k = 2 → -7. Docelowo O(n) czasu i O(1) pamięci.

Kontekst: nierozwiązany szkielet.

<!-- exercise:WIN-01 -->
```java
static long maxFixedSum(int[] values, int k) {
    throw new UnsupportedOperationException("TODO WIN-01");
}
```

Kontekst: wykonywalne testy.

<!-- tests:WIN-01 -->
```java
static void verify() {
    int[] input = {1, 4, 2, 10, 2, 3};
    assert maxFixedSum(input, 3) == 16;
    assert Arrays.equals(input, new int[] {1, 4, 2, 10, 2, 3});
    assert maxFixedSum(new int[] {-5, -2, -7}, 2) == -7;
    assert maxFixedSum(new int[] {4, -1, 2}, 1) == 4;
    assert maxFixedSum(new int[] {4, -1, 2}, 3) == 5;
    assert maxFixedSum(new int[] {8}, 1) == 8;
    assert maxFixedSum(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}, 2) == 4294967294L;

    Random random = new Random(301);
    for (int trial = 0; trial < 150; trial++) {
        int[] values = random.ints(10, -6, 7).toArray();
        int k = 1 + random.nextInt(values.length);
        long expected = Long.MIN_VALUE;
        for (int left = 0; left + k <= values.length; left++) {
            long sum = 0;
            for (int j = left; j < left + k; j++) { sum += values[j]; }
            expected = Math.max(expected, sum);
        }
        assert maxFixedSum(values, k) == expected : "seed=301 trial=" + trial;
    }
}
```

## WIN-02 — najwyżej k różnych znaków

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/03-sliding-window.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Warunek dotyczy liczby różnych znaków w oknie.

</details>

<details>
<summary>Wskazówka 2</summary>

Utrzymuj liczność każdego znaku.

</details>

<details>
<summary>Wskazówka 3</summary>

Zmieniając lewą granicę, aktualizuj liczbę różnych tylko przy przejściu przez zero.

</details>

**Treść i kontrakt.** Dla napisu ASCII zwróć długość najdłuższego spójnego
fragmentu zawierającego najwyżej k różnych znaków. Długość 0..100 000, k w 0..128.
Spacja i wielkość liter mają znaczenie. Pusty napis oraz k = 0 dają 0.
`eceba`, k = 2 → 3. Docelowo O(n) czasu i pamięć zależna od alfabetu.

Kontekst: nierozwiązany szkielet.

<!-- exercise:WIN-02 -->
```java
static int longestAtMostK(String text, int k) {
    throw new UnsupportedOperationException("TODO WIN-02");
}
```

Kontekst: wykonywalne testy.

<!-- tests:WIN-02 -->
```java
static void verify() {
    assert longestAtMostK("eceba", 2) == 3;
    assert longestAtMostK("aa", 1) == 2;
    assert longestAtMostK("abc", 0) == 0;
    assert longestAtMostK("", 2) == 0;
    assert longestAtMostK("abaccc", 2) == 4;
    assert longestAtMostK("abc", 128) == 3;
    assert longestAtMostK("aA a", 2) == 2;

    Random random = new Random(302);
    for (int trial = 0; trial < 150; trial++) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 10; i++) { text.append((char) ('a' + random.nextInt(4))); }
        int k = random.nextInt(5), expected = 0;
        for (int left = 0; left < text.length(); left++) {
            Set<Character> letters = new HashSet<>();
            for (int right = left; right < text.length(); right++) {
                letters.add(text.charAt(right));
                if (letters.size() <= k) { expected = Math.max(expected, right - left + 1); }
            }
        }
        assert longestAtMostK(text.toString(), k) == expected : "seed=302 trial=" + trial;
    }
}
```

## WIN-03 — najkrótszy fragment dodatnich liczb

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/03-sliding-window.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Dodatnie liczby pozwalają bezpiecznie skracać sumę.

</details>

<details>
<summary>Wskazówka 2</summary>

Po osiągnięciu celu próbuj skracać okno.

</details>

<details>
<summary>Wskazówka 3</summary>

Zapisuj długość przed usunięciem elementu; sumuj w long.

</details>

**Treść i kontrakt.** Dla tablicy ściśle dodatnich int i dodatniego target typu
long zwróć minimalną długość spójnego fragmentu o sumie co najmniej target.
Długość 0..100 000. Brak rozwiązania, także puste wejście, daje 0. Nie zmieniaj
tablicy. `[2,3,1,2,4,3]`, target 7 → 2. Docelowo O(n) czasu i O(1) pamięci.
Nie rozszerzaj kontraktu o liczby ujemne bez ponownego uzasadnienia algorytmu.

Kontekst: nierozwiązany szkielet.

<!-- exercise:WIN-03 -->
```java
static int minPositiveWindow(int[] values, long target) {
    throw new UnsupportedOperationException("TODO WIN-03");
}
```

Kontekst: wykonywalne testy.

<!-- tests:WIN-03 -->
```java
static void verify() {
    assert minPositiveWindow(new int[] {2, 3, 1, 2, 4, 3}, 7) == 2;
    assert minPositiveWindow(new int[] {1, 4, 4}, 4) == 1;
    assert minPositiveWindow(new int[] {1, 1, 1}, 4) == 0;
    assert minPositiveWindow(new int[0], 1) == 0;
    assert minPositiveWindow(new int[] {3}, 3) == 1;
    assert minPositiveWindow(new int[] {1, 2, 3}, 6) == 3;
    assert minPositiveWindow(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}, 4294967294L) == 2;
    assert minPositiveWindow(new int[] {1, 2}, Long.MAX_VALUE) == 0;
}
```
