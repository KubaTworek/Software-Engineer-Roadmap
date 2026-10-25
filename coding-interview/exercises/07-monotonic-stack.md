# Zadania: monotonic stack

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/07-monotonic-stack.md)

## STACK-01 — następny większy w cyklu

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/07-monotonic-stack.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Koniec może znaleźć odpowiedź na początku tablicy.

</details>

<details>
<summary>Wskazówka 2</summary>

Przechowuj nierozstrzygnięte indeksy.

</details>

<details>
<summary>Wskazówka 3</summary>

Wykonaj dwa przeglądy, dodając indeksy tylko raz; równa wartość nie rozstrzyga.

</details>

**Treść i kontrakt.** Dla każdego indeksu zwróć indeks pierwszego ściśle większego
elementu napotkanego w prawo, z zawinięciem na początek. Obejrzyj najwyżej n - 1
innych elementów; jeśli nie ma odpowiedzi, wpisz -1. Długość 0..100 000,
wartości int, bez mutacji. `[1,2,1]` → `[1,-1,1]`. Wyniki to indeksy, nie wartości.
Docelowo O(n) czasu i O(n) pamięci.

Kontekst: nierozwiązany szkielet.

<!-- exercise:STACK-01 -->
```java
static int[] nextGreaterCircular(int[] values) {
    throw new UnsupportedOperationException("TODO STACK-01");
}
```

Kontekst: wykonywalne testy.

<!-- tests:STACK-01 -->
```java
static void verify() {
    int[] input = {1, 2, 1};
    assert Arrays.equals(nextGreaterCircular(input), new int[] {1, -1, 1});
    assert Arrays.equals(input, new int[] {1, 2, 1});
    assert Arrays.equals(nextGreaterCircular(new int[] {3, 2, 1}), new int[] {-1, 0, 0});
    assert Arrays.equals(nextGreaterCircular(new int[] {2, 2}), new int[] {-1, -1});
    assert Arrays.equals(nextGreaterCircular(new int[] {7}), new int[] {-1});
    assert nextGreaterCircular(new int[0]).length == 0;
    assert Arrays.equals(nextGreaterCircular(new int[] {-3, -1, -2}), new int[] {1, -1, 1});
}
```

## STACK-02 — rozpiętość notowania

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/07-monotonic-stack.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Bieżący dzień może objąć rozpiętość wcześniejszych dni.

</details>

<details>
<summary>Wskazówka 2</summary>

Szukaj najbliższej wcześniejszej ściśle większej ceny.

</details>

<details>
<summary>Wskazówka 3</summary>

Zdejmuj ceny mniejsze lub równe; odległość licz do ostatniej przeszkody.

</details>

**Treść i kontrakt.** Dla każdego dnia i policz długość najdłuższego fragmentu
kończącego się w i, którego wszystkie ceny są mniejsze lub równe cenie w i.
Bieżący dzień wliczamy. Długość 0..100 000, dodatnie ceny int, bez mutacji.
`[100,80,60,70,60,75,85]` → `[1,1,1,2,1,4,6]`.
Docelowo O(n) czasu i O(n) pamięci.

Kontekst: nierozwiązany szkielet.

<!-- exercise:STACK-02 -->
```java
static int[] stockSpans(int[] prices) {
    throw new UnsupportedOperationException("TODO STACK-02");
}
```

Kontekst: wykonywalne testy; równość zwiększa rozpiętość.

<!-- tests:STACK-02 -->
```java
static void verify() {
    assert Arrays.equals(stockSpans(new int[] {100, 80, 60, 70, 60, 75, 85}),
            new int[] {1, 1, 1, 2, 1, 4, 6});
    assert Arrays.equals(stockSpans(new int[] {5, 5, 5}), new int[] {1, 2, 3});
    assert Arrays.equals(stockSpans(new int[] {1, 2, 3}), new int[] {1, 2, 3});
    assert Arrays.equals(stockSpans(new int[] {3, 2, 1}), new int[] {1, 1, 1});
    assert Arrays.equals(stockSpans(new int[] {9}), new int[] {1});
    assert stockSpans(new int[0]).length == 0;
}
```

## STACK-03 — największy prostokąt histogramu

**Poziom:** L3
**Wymagania:** [Karta techniki](../patterns/07-monotonic-stack.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Szerokość słupka ograniczają niższe słupki po obu stronach.

</details>

<details>
<summary>Wskazówka 2</summary>

Stos może czekać na pierwszą niższą wysokość po prawej.

</details>

<details>
<summary>Wskazówka 3</summary>

Przy zdjęciu użyj nowego szczytu jako lewej granicy i rozstrzygnij stos na końcu.

</details>

**Treść i kontrakt.** Słupki mają szerokość 1 i nieujemne wysokości int.
Zwróć największe pole prostokąta pod histogramem, jako long. Prostokąt obejmuje
spójny fragment słupków. Puste wejście → 0. n w 0..100 000. Bez mutacji.
`[2,1,5,6,2,3]` → 10. Docelowo O(n) czasu i O(n) pamięci.

Kontekst: nierozwiązany szkielet.

<!-- exercise:STACK-03 -->
```java
static long largestRectangle(int[] heights) {
    throw new UnsupportedOperationException("TODO STACK-03");
}
```

Kontekst: wykonywalne testy.

<!-- tests:STACK-03 -->
```java
static void verify() {
    int[] input = {2, 1, 5, 6, 2, 3};
    assert largestRectangle(input) == 10;
    assert Arrays.equals(input, new int[] {2, 1, 5, 6, 2, 3});
    assert largestRectangle(new int[] {2, 2, 2}) == 6;
    assert largestRectangle(new int[] {1, 2, 3}) == 4;
    assert largestRectangle(new int[] {3, 2, 1}) == 4;
    assert largestRectangle(new int[] {0, 0}) == 0;
    assert largestRectangle(new int[] {7}) == 7;
    assert largestRectangle(new int[0]) == 0;
    assert largestRectangle(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}) == 4294967294L;

    Random random = new Random(703);
    for (int trial = 0; trial < 150; trial++) {
        int[] heights = random.ints(10, 0, 8).toArray();
        long expected = 0;
        for (int left = 0; left < heights.length; left++) {
            int minimum = Integer.MAX_VALUE;
            for (int right = left; right < heights.length; right++) {
                minimum = Math.min(minimum, heights[right]);
                expected = Math.max(expected, (long) minimum * (right - left + 1));
            }
        }
        assert largestRectangle(heights) == expected : "seed=703 trial=" + trial;
    }
}
```
