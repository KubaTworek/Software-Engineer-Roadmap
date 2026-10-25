# Zadania: greedy

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/15-greedy.md)

Przed kodem zapisz regułę wyboru i argument, dlaczego istnieje optimum zgodne
z tym wyborem. Same przykłady testowe nie dowodzą zachłanności.

## GREEDY-01 — usuń najmniej kolidujących spotkań

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/15-greedy.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Minimalne usunięcia odpowiadają maksymalnej liczbie zachowanych.

</details>

<details>
<summary>Wskazówka 2</summary>

Najwcześniejszy koniec pozostawia miejsce kolejnym.

</details>

<details>
<summary>Wskazówka 3</summary>

Uzasadnij wymianę i zwróć usunięte, nie zachowane spotkania.

</details>

**Treść i kontrakt.** Spotkania są półotwartymi przedziałami int `[start,end)`,
`start < end`. Zwróć minimalną liczbę usuniętych spotkań, aby pozostałe parami
nie kolidowały. Wspólny koniec i początek nie kolidują. n w 0..100 000,
bez mutacji wejścia. `[[1,2],[2,3],[3,4],[1,3]]` → 1.
Docelowo O(n log n) czasu i O(n) pamięci z zachowaniem wejścia.

Kontekst: nierozwiązany szkielet.

<!-- exercise:GREEDY-01 -->
```java
static int minRemovals(int[][] meetings) {
    throw new UnsupportedOperationException("TODO GREEDY-01");
}
```

Kontekst: wykonywalne testy.

<!-- tests:GREEDY-01 -->
```java
static void verify() {
    int[][] input = {{1, 2}, {2, 3}, {3, 4}, {1, 3}};
    assert minRemovals(input) == 1;
    assert Arrays.deepEquals(input, new int[][] {{1, 2}, {2, 3}, {3, 4}, {1, 3}});
    assert minRemovals(new int[][] {{1, 2}, {1, 2}, {1, 2}}) == 2;
    assert minRemovals(new int[][] {{1, 2}, {2, 3}}) == 0;
    assert minRemovals(new int[][] {{0, 5}, {1, 2}, {2, 3}, {3, 4}}) == 1;
    assert minRemovals(new int[][] {{-4, -1}}) == 0;
    assert minRemovals(new int[0][]) == 0;

    Random random = new Random(1501);
    for (int trial = 0; trial < 80; trial++) {
        int[][] meetings = new int[8][2];
        for (int[] meeting : meetings) { meeting[0] = random.nextInt(8); meeting[1] = meeting[0] + 1 + random.nextInt(4); }
        int most = 0;
        for (int mask = 0; mask < (1 << meetings.length); mask++) {
            boolean valid = true;
            for (int i = 0; i < meetings.length; i++) {
                for (int j = i + 1; j < meetings.length; j++) {
                    if ((mask & (1 << i)) != 0 && (mask & (1 << j)) != 0
                            && meetings[i][0] < meetings[j][1] && meetings[j][0] < meetings[i][1]) { valid = false; }
                }
            }
            if (valid) { most = Math.max(most, Integer.bitCount(mask)); }
        }
        assert minRemovals(meetings) == meetings.length - most : "seed=1501 trial=" + trial;
    }
}
```

## GREEDY-02 — czy da się dojść do końca

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/15-greedy.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Wystarczy granica osiągalnego prefiksu.

</details>

<details>
<summary>Wskazówka 2</summary>

Przetwarzaj wyłącznie osiągalne indeksy.

</details>

<details>
<summary>Wskazówka 3</summary>

Rozszerzaj najdalszy indeks bez przepełnienia i nie przechodź przez nieosiągalną lukę.

</details>

**Treść i kontrakt.** Stoisz na indeksie 0 tablicy nieujemnych int. Z indeksu i
możesz skoczyć o dowolną liczbę pozycji od 1 do jumps[i], zawsze w prawo.
Sprawdź, czy można osiągnąć dokładnie ostatni indeks. Puste wejście → false;
jeden element → true, bez potrzeby skoku. n w 0..100 000, bez mutacji.
`[2,3,1,1,4]` → true; `[3,2,1,0,4]` → false. Docelowo O(n) czasu i O(1) pamięci.
Nie myl pytania o osiągalność z minimalną liczbą skoków.

Kontekst: nierozwiązany szkielet.

<!-- exercise:GREEDY-02 -->
```java
static boolean canReachEnd(int[] jumps) {
    throw new UnsupportedOperationException("TODO GREEDY-02");
}
```

Kontekst: wykonywalne testy, także dodawania indeksu i dużego skoku.

<!-- tests:GREEDY-02 -->
```java
static void verify() {
    int[] input = {2, 3, 1, 1, 4};
    assert canReachEnd(input);
    assert Arrays.equals(input, new int[] {2, 3, 1, 1, 4});
    assert !canReachEnd(new int[] {3, 2, 1, 0, 4});
    assert !canReachEnd(new int[0]);
    assert canReachEnd(new int[] {0});
    assert !canReachEnd(new int[] {0, 1});
    assert canReachEnd(new int[] {2, 0, 0});
    assert canReachEnd(new int[] {1, Integer.MAX_VALUE, 0, 0});
}
```

## GREEDY-03 — minimalna liczba punktów trafiających przedziały

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/15-greedy.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Łańcuch nakładań nie gwarantuje jednego wspólnego punktu.

</details>

<details>
<summary>Wskazówka 2</summary>

Rozważ koniec najwcześniej kończącego się nietrafionego przedziału.

</details>

<details>
<summary>Wskazówka 3</summary>

Traf wszystkie przedziały zawierające ten punkt i uzasadnij przesunięcie tam pierwszej strzały optimum.

</details>

**Treść i kontrakt.** Każdy balon jest domkniętym przedziałem int `[start,end]`,
`start <= end`. Strzała w punkcie x trafia wszystkie balony zawierające x.
Zwróć minimalną liczbę strzał potrzebnych do trafienia wszystkich balonów.
Strzały mogą mieć dowolną rzeczywistą współrzędną; zwracasz tylko ich liczbę.
n w 0..100 000, bez mutacji wejścia. `[[1,2],[2,3]]` → 1, bo wspólny punkt 2
należy do obu. Docelowo O(n log n) czasu i O(n) pamięci z zachowaniem wejścia.

Kontekst: nierozwiązany szkielet.

<!-- exercise:GREEDY-03 -->
```java
static int minArrows(int[][] balloons) {
    throw new UnsupportedOperationException("TODO GREEDY-03");
}
```

Kontekst: wykonywalne testy odróżniają wspólne przecięcie od łańcucha nakładań.

<!-- tests:GREEDY-03 -->
```java
static void verify() {
    int[][] input = {{10, 16}, {2, 8}, {1, 6}, {7, 12}};
    assert minArrows(input) == 2;
    assert Arrays.deepEquals(input, new int[][] {{10, 16}, {2, 8}, {1, 6}, {7, 12}});
    assert minArrows(new int[][] {{1, 2}, {2, 3}}) == 1;
    assert minArrows(new int[][] {{1, 2}, {2, 3}, {3, 4}}) == 2;
    assert minArrows(new int[][] {{1, 9}, {2, 3}, {4, 5}}) == 2;
    assert minArrows(new int[][] {{7, 7}, {7, 7}}) == 1;
    assert minArrows(new int[0][]) == 0;
    assert minArrows(new int[][] {{Integer.MIN_VALUE, -1}, {0, Integer.MAX_VALUE}}) == 2;
}
```
