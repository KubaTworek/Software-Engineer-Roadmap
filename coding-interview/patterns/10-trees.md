# 10. Drzewa i DFS — złożenie wyników poddrzew

[Katalog](../README.md) · [Grafy i BFS](11-graphs-bfs.md)

## Problem i kontrakt

Zwróć wysokość drzewa binarnego liczoną w węzłach na najdłuższej ścieżce
korzeń–liść. Pusty korzeń ma wysokość 0, pojedynczy węzeł 1. Wejście jest drzewem:
nie ma cykli ani współdzielonych poddrzew. Nie zmieniamy referencji.

## Od brute force do niezmiennika

Można wygenerować wszystkie ścieżki korzeń–liść i wybrać najdłuższą, lecz kopiowanie
ścieżek kosztuje dodatkowy czas i pamięć. Wystarczy zwracać wysokość poddrzewa.

**Niezmiennik rekurencji:** wywołanie `height(node)` zwraca wysokość dokładnie tego
poddrzewa. Dla null poprawna jest wartość 0. Jeśli wyniki dzieci są poprawne,
`1 + max(lewy, prawy)` jest poprawne dla rodzica. Indukcja po strukturze drzewa
uzasadnia wynik. Wywołania schodzą do ściśle mniejszych poddrzew.

## Przykład krok po kroku

Korzeń A ma liść B po lewej oraz C po prawej, a C ma liść D. B i D zwracają 1;
C zwraca 2, A zwraca 3. Nie sumujemy wysokości dzieci: ścieżka z korzenia nie
może zejść jednocześnie obiema gałęziami.

## Kod Java

Kontekst: składowe klasy; minimalny typ drzewa znajduje się w tym dokumencie.

```java
static final class Node {
    Node left;
    Node right;

    Node(Node left, Node right) {
        this.left = left;
        this.right = right;
    }
}

static int height(Node node) {
    if (node == null) {
        return 0;
    }
    return 1 + Math.max(height(node.left), height(node.right));
}
```

## Koszt i granice techniki

Czas O(n), pamięć pomocnicza O(h) na stos rekurencji, wynik O(1). Dla drzewa
zbalansowanego h = O(log n), dla łańcucha h = n. Głęboka rekurencja w Javie może
wywołać StackOverflowError; iteracyjny DFS wymaga jawnego stosu.
To nie jest wyszukiwanie BST — nie zakładamy porządku wartości węzłów.

## Kontrprzykład i scenariusze

Wzór `1 + height(left) + height(right)` liczy węzły, nie wysokość: korzeń z dwoma
liśćmi ma wysokość 2, a nie 3. Dla grafu z cyklem rekurencja bez visited nie kończy
się; kontrakt drzewa jest istotny.

Kontekst: wykonywane asercje dla różnych kształtów drzewa.

```java
static void verify() {
    assert height(null) == 0;
    assert height(new Node(null, null)) == 1;
    Node balanced = new Node(new Node(null, null), new Node(null, null));
    assert height(balanced) == 2;
    Node deeper = new Node(new Node(null, null), new Node(null, new Node(null, null)));
    assert height(deeper) == 3;
    Node chain = new Node(new Node(new Node(null, null), null), null);
    assert height(chain) == 3;
}
```

## Ćwiczenie

Sprawdź zbalansowanie drzewa w O(n). Zwracaj wysokość albo sygnał niezbalansowania.
Pokaż, dlaczego ponowne liczenie wysokości w każdym węźle może dać O(n²).
