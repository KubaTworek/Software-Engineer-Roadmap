# 23. BST — ograniczenie dziedziczone po przodkach

[Katalog](../README.md) · [Zadania](../exercises/23-bst.md)

## Problem i kontrakt

Sprawdź, czy acykliczne drzewo binarne jest ścisłym BST. Wszystkie wartości
w lewym poddrzewie muszą być mniejsze od węzła, w prawym większe. Duplikaty
są niedozwolone. Wartości int, null jest poprawnym pustym BST. Bez mutacji.

## Od brute force do niezmiennika

Ponowne skanowanie całych poddrzew w każdym węźle może dać O(n²). Wystarczy
nieść ograniczenia wszystkich przodków. **Niezmiennik:** ramka opisuje otwarty
przedział dopuszczalnych wartości w poddrzewie. Lewe dziecko dziedziczy dolną
granicę i dostaje wartość rodzica jako górną, prawe analogicznie dolną.

## Przykład krok po kroku

Korzeń 10, prawe dziecko 15, jego lewe dziecko 6: lokalnie 6 < 15, ale cały prawy
potomek korzenia musi być > 10. Dziedziczony zakres (10,15) ujawnia błąd.

## Kod Java

Kontekst: składowe klasy, java.util.*. long pozwala objąć wszystkie wartości int.

```java
static final class Node {
    final int value; Node left, right;
    Node(int value) { this.value = value; }
}
record Frame(Node node, long lower, long upper) {}

static boolean validBst(Node root) {
    Deque<Frame> stack = new ArrayDeque<>();
    if (root != null) { stack.push(new Frame(root, Long.MIN_VALUE, Long.MAX_VALUE)); }
    while (!stack.isEmpty()) {
        Frame frame = stack.pop(); Node node = frame.node();
        if (node.value <= frame.lower() || node.value >= frame.upper()) { return false; }
        // Każde dziecko zachowuje także ograniczenie po dalszych przodkach.
        if (node.left != null) { stack.push(new Frame(node.left, frame.lower(), node.value)); }
        if (node.right != null) { stack.push(new Frame(node.right, node.value, frame.upper())); }
    }
    return true;
}
```

## Koszt i granice techniki

Czas O(n), pamięć O(h) na jawnym stosie, wynik O(1). Wyszukiwanie w BST kosztuje
O(h), nie bezwarunkowo O(log n). Zdegenerowane drzewo ma h = n.
Porządek inorder ścisłego BST jest rosnący; zwykłego drzewa binarnego nie musi być.

## Kontrprzykład i scenariusze

Porównywanie wyłącznie dziecka z rodzicem pomija naruszenia względem dziadka.
Granice int jako otwarte sentinele błędnie wykluczyłyby MIN_VALUE lub MAX_VALUE.

Kontekst: wykonywane asercje.

```java
static void verify() {
    assert validBst(null);
    Node root = new Node(10); root.left = new Node(5); root.right = new Node(15);
    assert validBst(root);
    root.right.left = new Node(6); assert !validBst(root);
    root.right.left = new Node(10); assert !validBst(root);
    Node extremes = new Node(Integer.MIN_VALUE); extremes.right = new Node(Integer.MAX_VALUE);
    assert validBst(extremes);
}
```

## Ćwiczenie

Znajdź k-ty najmniejszy element bez kopiowania całego inorder do listy.
