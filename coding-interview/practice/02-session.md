# Trening mieszany

Najpierw nazwij technikę i uzasadnij wybór. Źródło z kategorią jest ukryte pod każdym zadaniem.

To samodzielny zestaw do edycji: uzupełniaj metody w tym pliku. Z katalogu coding-interview:

```powershell
pwsh -File scripts/check-exercises.ps1 -PracticeFile 'practice/02-session.md' -Exercise P01
```

Ziarno doboru: 43. Importy java.util.* zapewnia skrypt. Testy nie stanowią dowodu złożoności.

## P01 — liczba par indeksów o zadanej sumie

**Poziom:** L2

**Treść i kontrakt.** W niemalejącej tablicy int policz wszystkie pary indeksów
`i < j`, których matematyczna suma jest równa target typu int. Różne indeksy
liczą się osobno, także dla równych wartości. Długość 0..100 000. Wynik long,
bez mutacji. `[1,1,2,2,2]`, target 3 → 6. Docelowo O(n) czasu i O(1) pamięci.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P01 -->
```java
static long countPairs(int[] sorted, int target) {
    throw new UnsupportedOperationException("TODO P01");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P01 -->
```java
static void verify() {
    assert countPairs(new int[] {1, 1, 2, 2, 2}, 3) == 6;
    assert countPairs(new int[] {2, 2, 2, 2}, 4) == 6;
    assert countPairs(new int[] {-3, -1, 0, 1, 3}, 0) == 2;
    assert countPairs(new int[] {1}, 2) == 0;
    assert countPairs(new int[0], 0) == 0;
    assert countPairs(new int[] {1, 2, 3}, 9) == 0;
    assert countPairs(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}, -2) == 0;
    assert countPairs(new int[100_000], 0) == 4_999_950_000L;

    Random random = new Random(203);
    for (int trial = 0; trial < 200; trial++) {
        int[] values = random.ints(12, -4, 5).sorted().toArray();
        int target = random.nextInt(13) - 6;
        long expected = 0;
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                if ((long) values[i] + values[j] == target) { expected++; }
            }
        }
        assert countPairs(values, target) == expected : "seed=203 trial=" + trial;
    }
}
```

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:TWO-03 -->
<!-- snapshot:contract=67a38bc2ab9d1c19f4b64def914b1ba8988fc8b5d3c09ee194feb22d313111a7;tests=a4ba4bb847a74c19761e3772e137895e2f01598b0bbbf6ae0e0f184c960ed16d;stub=075a23deb8f793424fe47564ddd6df9e43b5e3282023da206146c23340928e91;rendered=65cea658c3586a36b2b4d106482d6a52355f44d9e20df127673c612fc8607618 -->
[Oryginał TWO-03](../exercises/02-two-pointers.md). Otwórz po własnej analizie.

</details>

## P02 — stabilnie scal dwie listy

Listy są acykliczne, do 100 000 węzłów. Typ Node jest częścią szkieletu. Tożsamość węzłów ma znaczenie; brak elementu oznacza null.

**Poziom:** L2

**Treść i kontrakt.** Dwie rozłączne listy są niemalejące. Scal je in-place w jedną niemalejącą listę z tych samych węzłów. Dla równych wartości najpierw pobieraj węzeł z pierwszej listy. Nie twórz kopii węzłów; pojedynczy pomocniczy węzeł-sentinel jest dozwolony i nie należy do wyniku. O(n+m) czasu, O(1) pamięci.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P02 -->
```java
static final class Node {
    final int value; Node next;
    Node(int value) { this.value = value; }
}
static Node mergeLists(Node first, Node second) {
    throw new UnsupportedOperationException("TODO P02");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P02 -->
```java
static void verify() {
    assert mergeLists(null,null)==null;
    Node a=new Node(1), b=new Node(3), c=new Node(1), d=new Node(2);
    a.next=b; c.next=d;
    Node result=mergeLists(a,c);
    assert result==a && a.next==c && c.next==d && d.next==b && b.next==null;
    Node x=new Node(7); assert mergeLists(null,x)==x && x.next==null;
}
```

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:LIST-02 -->
<!-- snapshot:contract=02dd3121c529ca45442c2c5e094e05b17a86b2f2f6ed3043fab596e7a5d15911;tests=08886ae18951a812e206dc2c1365cd47be78a6680caf2b4f35936fe996e4d6b5;stub=161dae8f6e81b1f438b4b0a7118085cb75805808ae7e5447e0f9d6911f70aed3;rendered=2f4d7b6911338f88f852f8d7ffc7b4d4d3f35d758ab994a9b40768012e161aea -->
[Oryginał LIST-02](../exercises/21-linked-lists.md). Otwórz po własnej analizie.

</details>

## P03 — istnienie prefiksów

Każde zadanie jest niezależne; metody pozostają TODO. Importy java.util.* zapewnia skrypt.

**Poziom:** L1

**Treść i kontrakt.** Dla słów a–z i zapytań o prefiksy zwróć boolean[] w kolejności zapytań. Pusty prefiks jest poprawną ścieżką nawet przy pustym słowniku; inne wymagają co najmniej jednego słowa z takim początkiem. Suma długości wejść do 100 000, bez mutacji. Docelowy koszt liniowy w sumie długości.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P03 -->
```java
static boolean[] prefixQueries(String[] words, String[] prefixes) {
    throw new UnsupportedOperationException("TODO P03");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P03 -->
```java
static void verify() {
    assert Arrays.equals(prefixQueries(new String[] {"kot","kotek","pies"}, new String[] {"ko","kot","kota","","pie"}), new boolean[] {true,true,false,true,true});
    assert Arrays.equals(prefixQueries(new String[0], new String[] {"","a"}), new boolean[] {true,false});
    assert prefixQueries(new String[] {""}, new String[0]).length == 0;
}
```

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:TRIE-01 -->
<!-- snapshot:contract=85078586b334c10695df6f407700ffc31c4f735f9f853612a47cdcb4a9df376e;tests=baab07fc33f9cca1bdc7e74e490c87de3f2b1ac9d77c81cde23246d7e8f3221b;stub=1d5288b648283cc4664e70a7db96153cc04578cbe67b7c00db28360acf1fc5e9;rendered=bfd982ebd585b2688116c0d28a8f4e7f40ac8b5872e636d19e6d4a42441105ca -->
[Oryginał TRIE-01](../exercises/17-trie.md). Otwórz po własnej analizie.

</details>

## P04 — czy wszystkie kursy są wykonalne

Grafy mają poprawne indeksy, mogą mieć cykle, pętle i powtarzające się krawędzie.
V i E do 100 000. Wejścia pozostają bez zmian. Listy sąsiedztwa nie zawierają null.

**Poziom:** L1

**Treść i kontrakt.** Kursy mają numery 0..courses-1. Para `[course, prerequisite]`
oznacza, że prerequisite musi zostać ukończony przed course. Sprawdź, czy można
ukończyć wszystkie kursy. Dla 0 kursów i braku par wynik to true. Brak zależności
nie blokuje kursu. Docelowo O(V + E) czasu, O(V + E) pamięci łącznie z reprezentacją
grafu utworzoną z par.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P04 -->
```java
static boolean canFinish(int courses, int[][] prerequisites) {
    throw new UnsupportedOperationException("TODO P04");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P04 -->
```java
static void verify() {
    int[][] input = {{1, 0}, {2, 1}};
    assert canFinish(3, input);
    assert Arrays.deepEquals(input, new int[][] {{1, 0}, {2, 1}});
    assert !canFinish(2, new int[][] {{1, 0}, {0, 1}});
    assert !canFinish(1, new int[][] {{0, 0}});
    assert !canFinish(4, new int[][] {{1, 0}, {2, 3}, {3, 2}});
    assert canFinish(2, new int[][] {{1, 0}, {1, 0}});
    assert canFinish(5, new int[0][]);
    assert canFinish(0, new int[0][]);
}
```

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:TOPO-01 -->
<!-- snapshot:contract=8d33a0b5030b3b0a7326bfaec53c32ac21012228afe92eba251022321de84760;tests=2492b4ccedbd1e8c811ff9e0420a968ec2fb1609abbc3dbb93b8c9b2dce65ff8;stub=210da1ad41e05481380a98337ad4c7cd223b81fd476a36fe27502a4a3757e12e;rendered=d237caecb5fd31ebf8e68aca173e4f32b180a7ed246f69e09019648b8f2248eb -->
[Oryginał TOPO-01](../exercises/12-topological-sort.md). Otwórz po własnej analizie.

</details>

## P05 — najbliższe punkty z remisami

**Poziom:** L3

**Treść i kontrakt.** Dla punktów `[x,y]` zwróć k najbliższych początkowi układu,
uporządkowanych po rosnącej odległości, następnie po x, następnie po y. Duplikaty
punktów zachowaj. Współrzędne są w [-1 000 000 000, 1 000 000 000],
`0 <= k <= n <= 100 000`. Kwadrat odległości mieści się w long. Nie zmieniaj
wejścia; wynik ma własne wiersze. Docelowo O(n log(k + 1) + k log(k + 1)) czasu,
O(k) pamięci. Nie potrzebujesz pierwiastków do porównywania odległości.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P05 -->
```java
static int[][] closestPoints(int[][] points, int k) {
    throw new UnsupportedOperationException("TODO P05");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P05 -->
```java
static void verify() {
    int[][] input = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {9, 9}};
    int[][] result = closestPoints(input, 3);
    assert Arrays.deepEquals(result, new int[][] {{-1, 0}, {0, -1}, {0, 1}});
    result[0][0] = 99;
    assert Arrays.deepEquals(input, new int[][] {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {9, 9}});
    assert Arrays.deepEquals(closestPoints(new int[][] {{1, 1}, {1, 1}, {0, 0}}, 3),
            new int[][] {{0, 0}, {1, 1}, {1, 1}});
    assert closestPoints(new int[0][], 0).length == 0;
    assert closestPoints(new int[][] {{1, 2}}, 0).length == 0;
    assert Arrays.deepEquals(closestPoints(new int[][] {{1_000_000_000, 1_000_000_000}, {3, 4}}, 1),
            new int[][] {{3, 4}});
}
```

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:HEAP-02 -->
<!-- snapshot:contract=b3d7a11abe59103db0384a8318bf0e2e4de51c886458c243efcb34b803d2f19d;tests=d6d0bfb3edd1bb8ea99b1d7740cc3149e25d96e96593a0bcfa6fdfc95a7938ca;stub=12f2db5a5edbe58cad96bb04e03df53eb7363c2b31642ade70fe2e1ed1998e1a;rendered=f29650ec2c653291f82f0b77e278c720333358de0d199abb6d519050b943fb82 -->
[Oryginał HEAP-02](../exercises/09-heap.md). Otwórz po własnej analizie.

</details>

## P06 — minimalna liczba sal

**Poziom:** L2

**Treść i kontrakt.** Wszystkie spotkania muszą się odbyć. Są półotwartymi
przedziałami int `[start,end)`, `start < end`. Zwróć minimalną liczbę sal, aby
spotkania w jednej sali nie zachodziły na siebie. Koniec o 10 pozwala zacząć
następne o 10. n w 0..100 000, bez mutacji wejścia. `[[0,30],[5,10],[15,20]]`
→ 2. Docelowo O(n log n) czasu i O(n) pamięci.

**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią.

Kontekst: szkielet do uzupełnienia.

<!-- exercise:P06 -->
```java
static int minRooms(int[][] meetings) {
    throw new UnsupportedOperationException("TODO P06");
}
```

Kontekst: wykonywalne testy.

<!-- tests:P06 -->
```java
static void verify() {
    int[][] input = {{15, 20}, {0, 30}, {5, 10}};
    assert minRooms(input) == 2;
    assert Arrays.deepEquals(input, new int[][] {{15, 20}, {0, 30}, {5, 10}});
    assert minRooms(new int[][] {{1, 2}, {2, 3}}) == 1;
    assert minRooms(new int[][] {{1, 4}, {1, 4}, {1, 4}}) == 3;
    assert minRooms(new int[][] {{0, 10}, {1, 9}, {2, 8}}) == 3;
    assert minRooms(new int[][] {{-5, -1}, {-1, 2}}) == 1;
    assert minRooms(new int[0][]) == 0;
}
```

<details>
<summary>Pokaż kategorię i wskazówki</summary>

<!-- origin:INT-03 -->
<!-- snapshot:contract=a09637a2795a63ad48af9beb05237e7daabd899bebe69209f61a5aaecfc53ca7;tests=702afa93300c454e3f8627469da9256ecc7cb28b1eaf376243dd9782f5577a9b;stub=d845851055b3b848881e1bcc2485172d6d84348229198026c43a01786280534e;rendered=83892f987fef3be7ddf0aeb1a29f4f91e77b33cfe3938a1da4fdcbbe312fecf7 -->
[Oryginał INT-03](../exercises/06-intervals.md). Otwórz po własnej analizie.

</details>
