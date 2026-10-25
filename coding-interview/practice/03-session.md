# Trening mieszany

Najpierw nazwij technikę i uzasadnij wybór. Źródło z kategorią jest ukryte pod każdym zadaniem.

To samodzielny zestaw do edycji: uzupełniaj metody w tym pliku. Z katalogu coding-interview:

```powershell
pwsh -File scripts/check-exercises.ps1 -PracticeFile 'practice/03-session.md' -Exercise P01
```

Ziarno doboru: 44. Importy java.util.* zapewnia skrypt. Testy nie stanowią dowodu złożoności.

## P01 — największy prostokąt histogramu

**Poziom:** L3

**Treść i kontrakt.** Słupki mają szerokość 1 i nieujemne wysokości int.
Zwróć największe pole prostokąta pod histogramem, jako long. Prostokąt obejmuje
spójny fragment słupków. Puste wejście → 0. n w 0..100 000. Bez mutacji.
`[2,1,5,6,2,3]` → 10. Docelowo O(n) czasu i O(n) pamięci.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P01 -->
```java
static long largestRectangle(int[] heights) {
    throw new UnsupportedOperationException("TODO P01");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P01 -->
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

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:STACK-03 -->
<!-- snapshot:contract=8f576fd7b1151e908408b4dbe67cf446a6dabc669ca43a5aba7ae058252cd1d3;tests=585df23ad6ceac5910611119154133e52c819ec09d3143925f5eb9afbcef5e6b;stub=b9757fc120e8adece65ec724215a6b35bb8fbe291cadc04cb5ae51c44e494c90;rendered=fe923597f3be692a45c1d010dfa8ec33a29c1515d48a2e6b313e381b7ef0b8e9 -->
[Oryginał STACK-03](../exercises/07-monotonic-stack.md). Otwórz po własnej analizie.

</details>

## P02 — fragmenty o sumie podzielnej przez d

**Poziom:** L2

**Treść i kontrakt.** Policz niepuste spójne fragmenty tablicy int, których suma
jest podzielna przez dodatnie d. Wartości mogą być ujemne; n w 0..100 000,
`1 <= d <= 100 000`. Wynik long, bez mutacji. Dla `[4,5,0,-2,-3,1]`, d = 5
wynik wynosi 7. Docelowo O(n + d) czasu i O(d) pamięci albo oczekiwane O(n)
z pamięcią O(min(n + 1, d)). Ujemna reszta Javy nie zmienia matematycznej podzielności.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P02 -->
```java
static long countDivisible(int[] values, int d) {
    throw new UnsupportedOperationException("TODO P02");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P02 -->
```java
static void verify() {
    assert countDivisible(new int[] {4, 5, 0, -2, -3, 1}, 5) == 7;
    assert countDivisible(new int[] {-1, 5}, 5) == 1;
    assert countDivisible(new int[] {0, 0}, 3) == 3;
    assert countDivisible(new int[] {1, 2, 3}, 1) == 6;
    assert countDivisible(new int[] {1}, 2) == 0;
    assert countDivisible(new int[0], 7) == 0;
    assert countDivisible(new int[100_000], 1) == 5_000_050_000L;
}
```

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:PRE-03 -->
<!-- snapshot:contract=75b54e60911f1a62f11a5ccf2ed2403ddd0edf5764d45e8942cf6a5cb38f2f9a;tests=20ca68dd26ce1a13bc44a99eeb1de2bddb1c8329acf43e432f2e9ad867aa14c0;stub=e0af5d02076c71e06cd4216283737e4e4d76fe2bf680b53ddc6a882314d342cd;rendered=ebb1423bad3fa74b61a2835c2d556daa215c7a361a9c7b97746bf8349ddc2769 -->
[Oryginał PRE-03](../exercises/04-prefix-sum.md). Otwórz po własnej analizie.

</details>

## P03 — poprawne nawiasy

Każde zadanie jest niezależne; metody pozostają TODO. Importy java.util.* zapewnia skrypt.

**Poziom:** L1

**Treść i kontrakt.** Napis zawiera tylko ()[]{}. Sprawdź, czy nawiasy są poprawnie zagnieżdżone i domknięte. Pusty napis daje true. Długość do 100 000, O(n) czasu i O(n) pamięci.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P03 -->
```java
static boolean validBrackets(String text) {
    throw new UnsupportedOperationException("TODO P03");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P03 -->
```java
static void verify() {
    assert validBrackets("([]{})"); assert validBrackets("");
    assert !validBrackets("([)]"); assert !validBrackets("]");
    assert !validBrackets("(()"); assert validBrackets("()[]{}");
}
```

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:SQ-01 -->
<!-- snapshot:contract=24c71e99b89ed0314801ec74f9e30e3ffe087b86b545a3619e0f97d16cb3d7a9;tests=0c9643d1a076022ce0d3617f2d01b48deb0f37b32ff3e4b1d81addde28c0dbf6;stub=54545a9369e0bca005c4735b51b0ee1a750ababe4bd97d6da3590840860dae90;rendered=d11e0773abfff85a2296b967c5dee19c5b35c9a734fe1677b0d408ab30b44287 -->
[Oryginał SQ-01](../exercises/22-stack-queue.md). Otwórz po własnej analizie.

</details>

## P04 — pierwszy węzeł cyklu

Każdy szkielet ma własny minimalny typ Node. Wartość służy tylko do zbudowania
kontrprzykładów: równe wartości nie oznaczają tego samego węzła. Współbieżna
modyfikacja struktury nie występuje. Limit to 100 000 osiągalnych węzłów.

**Poziom:** L2

**Treść i kontrakt.** Zwróć referencję do pierwszego węzła cyklu osiągalnego
z head albo null, jeśli cyklu nie ma. Nie zmieniaj żadnego połączenia. Pusta
lista → null. Docelowo O(n) czasu, O(1) pamięci.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P04 -->
```java
static final class Node {
    final int value;
    Node next;
    Node(int value) { this.value = value; }
}

static Node cycleEntry(Node head) {
    throw new UnsupportedOperationException("TODO P04");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P04 -->
```java
static void verify() {
    assert cycleEntry(null) == null;
    Node a = new Node(7), b = new Node(7), c = new Node(7), d = new Node(7);
    a.next = b; b.next = c; c.next = d;
    assert cycleEntry(a) == null;
    d.next = b;
    assert cycleEntry(a) == b;
    assert a.next == b && b.next == c && c.next == d && d.next == b;
    d.next = a;
    assert cycleEntry(a) == a;
    Node self = new Node(1);
    self.next = self;
    assert cycleEntry(self) == self;
}
```

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:FAST-01 -->
<!-- snapshot:contract=fc2fe85cf631055bcecb20a5c8ec82384dbc92d19ad2726dc3b38bb0479bcc12;tests=91ec42bca2889f5a126f135321278334626cd48ad8b2e899b935625da2d74658;stub=ff6c9802895ae523a94110dd0fbfc06b1b28e6c82c47ff8a39fabf0ea7072e3f;rendered=2105f456b5644a7573a705ac9dae431d550a3f5879626729fa498e1601555d16 -->
[Oryginał FAST-01](../exercises/08-fast-slow.md). Otwórz po własnej analizie.

</details>

## P05 — unikalne permutacje multizbioru

Wyniki muszą być kompletne i bez powtórzeń. Kolejność różnych wyników nie jest
narzucona. Listy reprezentujące różne wyniki nie mogą współdzielić modyfikowalnego
bufora. Testy nie wymagają, aby zwracane kolekcje były modyfikowalne.

**Poziom:** L2

**Treść i kontrakt.** Zwróć wszystkie różne permutacje tablicy int z możliwymi
duplikatami. Użyj każdego elementu dokładnie raz. n w 0..8; puste wejście ma
jedną permutację: pustą listę. Nie zmieniaj wejścia.
`[1,1,2]` daje dokładnie trzy wyniki. Koszt ma obejmować zapis pełnych permutacji;
uzasadnij, że nie generujesz tej samej permutacji wiele razy.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P05 -->
```java
static List<List<Integer>> uniquePermutations(int[] values) {
    throw new UnsupportedOperationException("TODO P05");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P05 -->
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

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:BACK-02 -->
<!-- snapshot:contract=aa2a0a9cf3cc3ae70a70604f7f3111a74a8faea672e56432278841f22789209f;tests=d0b0c2a06e4d12e68bbb7e4f59f6e563dcaaec634b31254e0fd68b651b51b1ae;stub=95a6ba2a8ee26636fd00f1d5b4737300ab46cfeccf52610cdfa539337e4ccd43;rendered=29a828b632484ffec20e17942a555b3fc5acd9ed1a076acda97fb2b391639cb7 -->
[Oryginał BACK-02](../exercises/13-backtracking.md). Otwórz po własnej analizie.

</details>

## P06 — dwie pojedyncze wartości

Każde zadanie jest niezależne; metody pozostają TODO. Importy java.util.* zapewnia skrypt.

**Poziom:** L2

**Treść i kontrakt.** W niepustej tablicy dokładnie dwie różne wartości int występują raz, wszystkie pozostałe dwa razy. Zwróć dwie pojedyncze wartości rosnąco. n w 2..100 000, bez mutacji, O(n) czasu i O(1) pamięci.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P06 -->
```java
static int[] twoSingles(int[] values) {
    throw new UnsupportedOperationException("TODO P06");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P06 -->
```java
static void verify() {
    assert Arrays.equals(twoSingles(new int[] {1,2,1,3,2,5}), new int[] {3,5});
    assert Arrays.equals(twoSingles(new int[] {0,-1}), new int[] {-1,0});
    assert Arrays.equals(twoSingles(new int[] {Integer.MIN_VALUE,0,7,7}), new int[] {Integer.MIN_VALUE,0});
    assert Arrays.equals(twoSingles(new int[] {-4,-2,3,3}), new int[] {-4,-2});
}
```

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:BIT-03 -->
<!-- snapshot:contract=7cfff0d9b8350d8d2ca084a5c305fa31061fcd653544a1c2e0af27e1d9ceebec;tests=880320dc94827e814b2f6316faa065905454c177cd30005d671f4ab89ee18bb4;stub=d92224bd5d3f80a457d3f692a5f067677bede129ee9a467fd4b07373812cf24d;rendered=7993ea4bc7008783e5f18d80366fa6154b6140bb2acd854e009b4a5a6a262be1 -->
[Oryginał BIT-03](../exercises/18-bit-operations.md). Otwórz po własnej analizie.

</details>
