# Zadania: drzewa i DFS

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/10-trees.md)

W każdym zadaniu wejście jest drzewem binarnym: bez cykli i współdzielonych
poddrzew. Pusty korzeń to null. Nie zmieniaj węzłów ani połączeń. Limit n to
10 000 węzłów; głęboki łańcuch jest dopuszczalny. Testy obejmują łańcuch
10 000 węzłów; rozwiązanie ma obsłużyć go bez przepełnienia stosu Javy.

## TREE-01 — przejście inorder

**Poziom:** L1
**Wymagania:** [Karta techniki](../patterns/10-trees.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Inorder nie jest sortowaniem zwykłego drzewa.

</details>

<details>
<summary>Wskazówka 2</summary>

Jawny stos pamięta powroty z lewej gałęzi.

</details>

<details>
<summary>Wskazówka 3</summary>

Schodź w lewo, przetwarzaj zdjęty węzeł, następnie idź w prawo.

</details>

**Treść i kontrakt.** Zwróć listę wartości z przejścia lewy potomek → węzeł →
prawy potomek. To nie musi być BST, więc wynik nie musi być posortowany.
Zachowaj duplikaty wartości. Pusty korzeń daje pustą listę. Docelowo O(n) czasu
i O(h) pamięci pomocniczej poza wynikiem O(n), gdzie h jest wysokością drzewa.

Kontekst: typ wejścia i nierozwiązana metoda.

<!-- exercise:TREE-01 -->
```java
static final class Node {
    final int value;
    Node left, right;
    Node(int value) { this.value = value; }
}

static List<Integer> inorder(Node root) {
    throw new UnsupportedOperationException("TODO TREE-01");
}
```

Kontekst: wykonywalne testy.

<!-- tests:TREE-01 -->
```java
static void verify() {
    assert inorder(null).isEmpty();
    assert inorder(new Node(7)).equals(List.of(7));
    Node root = new Node(5), left = new Node(9), right = new Node(1), inner = new Node(5);
    root.left = left; root.right = right; left.right = inner;
    assert inorder(root).equals(List.of(9, 5, 5, 1));
    assert root.left == left && root.right == right && left.right == inner;
    assert left.left == null && inner.left == null && inner.right == null;
    assert right.left == null && right.right == null;
    Node chain = new Node(1);
    chain.right = new Node(2); chain.right.right = new Node(3);
    assert inorder(chain).equals(List.of(1, 2, 3));

    Node deep = new Node(0), tail = deep;
    for (int i = 1; i < 10_000; i++) { tail.right = new Node(i); tail = tail.right; }
    List<Integer> traversal = inorder(deep);
    assert traversal.size() == 10_000;
    for (int i = 0; i < traversal.size(); i++) { assert traversal.get(i) == i; }
}
```

## TREE-02 — zbalansowanie każdego poddrzewa

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/10-trees.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Rodzic potrzebuje wysokości i poprawności dzieci.

</details>

<details>
<summary>Wskazówka 2</summary>

Ponowne liczenie wysokości w każdym węźle powtarza pracę.

</details>

<details>
<summary>Wskazówka 3</summary>

Przetwarzaj dzieci przed rodzicem i propaguj niepoprawność; rozważ fazy jawnego stosu.

</details>

**Treść i kontrakt.** Sprawdź, czy w każdym węźle różnica wysokości jego poddrzew
jest nie większa niż 1. Warunek dotyczy wszystkich węzłów, nie tylko korzenia.
Puste drzewo jest zbalansowane. Docelowo O(n) czasu; podaj pamięć swojej wersji.

Kontekst: typ wejścia i nierozwiązana metoda.

<!-- exercise:TREE-02 -->
```java
static final class Node {
    Node left, right;
}

static boolean isBalanced(Node root) {
    throw new UnsupportedOperationException("TODO TREE-02");
}
```

Kontekst: wykonywalne testy, w tym błąd ukryty poniżej korzenia.

<!-- tests:TREE-02 -->
```java
static void verify() {
    assert isBalanced(null);
    assert isBalanced(new Node());
    Node root = new Node();
    root.left = new Node(); root.right = new Node();
    assert isBalanced(root);
    Node chain = new Node();
    chain.left = new Node(); chain.left.left = new Node();
    assert !isBalanced(chain);
    Node hidden = new Node();
    hidden.left = new Node(); hidden.right = new Node();
    hidden.left.left = new Node(); hidden.left.left.left = new Node();
    hidden.right.right = new Node(); hidden.right.right.right = new Node();
    assert !isBalanced(hidden);
    assert hidden.left.left.left != null && hidden.right.right.right != null;

    Node deep = new Node(), tail = deep;
    for (int i = 1; i < 10_000; i++) { tail.right = new Node(); tail = tail.right; }
    assert !isBalanced(deep);
}
```

## TREE-03 — średnica w krawędziach

**Poziom:** L3
**Wymagania:** [Karta techniki](../patterns/10-trees.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Najlepsza ścieżka może nie dotykać korzenia.

</details>

<details>
<summary>Wskazówka 2</summary>

Rodzic potrzebuje długości ramienia, a globalny wynik może leżeć niżej.

</details>

<details>
<summary>Wskazówka 3</summary>

Rozważ połączenie obu ramion w każdym węźle, odróżniając krawędzie od węzłów.

</details>

**Treść i kontrakt.** Zwróć maksymalną liczbę krawędzi na prostej ścieżce między
dowolnymi dwoma węzłami drzewa. Ścieżka nie musi przechodzić przez korzeń.
Puste drzewo i jeden węzeł dają 0. Docelowo O(n) czasu; podaj koszt pamięci.

Kontekst: typ wejścia i nierozwiązana metoda.

<!-- exercise:TREE-03 -->
```java
static final class Node {
    Node left, right;
}

static int diameterEdges(Node root) {
    throw new UnsupportedOperationException("TODO TREE-03");
}
```

Kontekst: wykonywalne testy; ostatni przykład ma optimum wewnątrz lewego poddrzewa.

<!-- tests:TREE-03 -->
```java
static void verify() {
    assert diameterEdges(null) == 0;
    assert diameterEdges(new Node()) == 0;
    Node root = new Node();
    root.left = new Node(); root.right = new Node();
    assert diameterEdges(root) == 2;
    root.left.left = new Node(); root.left.left.left = new Node();
    assert diameterEdges(root) == 4;
    Node inner = new Node();
    inner.left = new Node();
    inner.left.left = new Node(); inner.left.right = new Node();
    inner.left.left.left = new Node(); inner.left.left.left.left = new Node();
    inner.left.right.right = new Node(); inner.left.right.right.right = new Node();
    assert diameterEdges(inner) == 6;
    assert diameterEdges(root) == 4;

    Node deep = new Node(), tail = deep;
    for (int i = 1; i < 10_000; i++) { tail.right = new Node(); tail = tail.right; }
    assert diameterEdges(deep) == 9_999;
}
```
