# Zadania: BST

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/23-bst.md)

Drzewo jest acykliczne bez współdzielonych poddrzew, do 10 000 węzłów; głęboki łańcuch dozwolony. Nie zmieniaj wejścia. Null oznacza puste drzewo.

## BST-01 — sprawdź ścisłe BST

**Poziom:** L1
**Wymagania:** [BST](../patterns/23-bst.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Sprawdź, czy każde lewe poddrzewo ma wartości ściśle mniejsze od przodka, a prawe ściśle większe. Wartości int, duplikaty zabronione, puste drzewo poprawne. O(n) czasu i O(h) pamięci.

<details>
<summary>Wskazówka 1</summary>

Węzeł może naruszać regułę dziadka mimo poprawnej relacji z rodzicem.

</details>

<details>
<summary>Wskazówka 2</summary>

Przekazuj dopuszczalny otwarty zakres lub kontroluj ścisły porządek inorder.

</details>

<details>
<summary>Wskazówka 3</summary>

Nie używaj MIN_VALUE/MAX_VALUE int jako wyłączonych granic, jeśli te wartości są poprawnym wejściem.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:BST-01 -->
```java
static final class Node {
    final int value; Node left, right;
    Node(int value) { this.value=value; }
}
static boolean isStrictBst(Node root) {
    throw new UnsupportedOperationException("TODO BST-01");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:BST-01 -->
```java
static void verify() {
    assert isStrictBst(null);
    Node r=new Node(10); r.left=new Node(5); r.right=new Node(15); assert isStrictBst(r);
    r.right.left=new Node(6); assert !isStrictBst(r);
    r.right.left=new Node(10); assert !isStrictBst(r);
    Node x=new Node(Integer.MIN_VALUE); x.right=new Node(Integer.MAX_VALUE); assert isStrictBst(x);

    Node deep = new Node(0), tail = deep;
    for (int i = 1; i < 10_000; i++) { tail.right = new Node(i); tail = tail.right; }
    assert isStrictBst(deep);
}
```

## BST-02 — k-ty najmniejszy element

**Poziom:** L2
**Wymagania:** [BST](../patterns/23-bst.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Wejście jest niepustym poprawnym ścisłym BST. Zwróć k-ty najmniejszy element, k od 1 do liczby węzłów. Nie materializuj całego inorder; przerwij po znalezieniu wyniku. Docelowo O(h+k) czasu i O(h) pamięci.

<details>
<summary>Wskazówka 1</summary>

Inorder BST jest uporządkowany, ale nie trzeba zapisywać wszystkich wartości.

</details>

<details>
<summary>Wskazówka 2</summary>

Przechowaj na stosie ścieżkę do następnego odwiedzanego węzła.

</details>

<details>
<summary>Wskazówka 3</summary>

Zmniejszaj licznik przy przetworzeniu węzła, nie przy jego odłożeniu na stos.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:BST-02 -->
```java
static final class Node {
    final int value; Node left, right;
    Node(int value) { this.value=value; }
}
static int kthSmallest(Node root, int k) {
    throw new UnsupportedOperationException("TODO BST-02");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:BST-02 -->
```java
static void verify() {
    Node r=new Node(5); r.left=new Node(2); r.right=new Node(9);
    r.left.left=new Node(1); r.left.right=new Node(3);
    assert kthSmallest(r,1)==1; assert kthSmallest(r,3)==3; assert kthSmallest(r,5)==9;
    assert kthSmallest(new Node(Integer.MIN_VALUE),1)==Integer.MIN_VALUE;
}
```

## BST-03 — suma w zakresie

**Poziom:** L2
**Wymagania:** [BST](../patterns/23-bst.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Wejście jest poprawnym ścisłym BST. Zsumuj wartości w domkniętym zakresie [low,high], low≤high, obie granice int. Wynik long, puste drzewo daje 0. Docelowo O(n) w najgorszym przypadku z odcinaniem poddrzew, które nie mogą zawierać wyniku; O(h) pamięci.

<details>
<summary>Wskazówka 1</summary>

Które całe poddrzewo można pominąć przy wartości mniejszej niż low?

</details>

<details>
<summary>Wskazówka 2</summary>

Porządek BST daje decyzję osobno dla lewego i prawego potomka.

</details>

<details>
<summary>Wskazówka 3</summary>

Sumuj w long; warunki odcięcia nie mogą zgubić wartości równej granicy.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:BST-03 -->
```java
static final class Node {
    final int value; Node left, right;
    Node(int value) { this.value=value; }
}
static long rangeSum(Node root, int low, int high) {
    throw new UnsupportedOperationException("TODO BST-03");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:BST-03 -->
```java
static void verify() {
    Node r=new Node(10); r.left=new Node(5); r.right=new Node(15);
    r.left.left=new Node(3); r.left.right=new Node(7); r.right.right=new Node(18);
    assert rangeSum(r,7,15)==32; assert rangeSum(r,10,10)==10;
    assert rangeSum(r,20,30)==0; assert rangeSum(null,0,1)==0;
    Node x=new Node(Integer.MAX_VALUE); x.left=new Node(Integer.MAX_VALUE-1);
    assert rangeSum(x,Integer.MIN_VALUE,Integer.MAX_VALUE)==4294967293L;
}
```
