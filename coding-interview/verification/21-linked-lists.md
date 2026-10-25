# Kontrola testów — 21-linked-lists

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja LIST-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:LIST-01 -->
```java
static final class Node {
    final int value; Node next;
    Node(int value) { this.value = value; }
}
static Node reverseList(Node head) {
    Node previous = null, current = head;
    while (current != null) { Node following = current.next; current.next = previous; previous = current; current = following; }
    return previous;
}
```

## Referencja LIST-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:LIST-02 -->
```java
static final class Node {
    final int value; Node next;
    Node(int value) { this.value = value; }
}
static Node mergeLists(Node first, Node second) {
    Node dummy = new Node(0), tail = dummy;
    while (first != null && second != null) {
        if (first.value <= second.value) { tail.next = first; first = first.next; } else { tail.next = second; second = second.next; }
        tail = tail.next;
    }
    tail.next = first != null ? first : second; return dummy.next;
}
```

### Mutacja niestabilny-remis

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:LIST-02:niestabilny-remis -->
```java
static final class Node {
    final int value; Node next;
    Node(int value) { this.value = value; }
}
static Node mergeLists(Node first, Node second) {
    Node dummy = new Node(0), tail = dummy;
    while (first != null && second != null) {
        if (first.value < second.value) { tail.next = first; first = first.next; } else { tail.next = second; second = second.next; }
        tail = tail.next;
    }
    tail.next = first != null ? first : second; return dummy.next;
}
```

## Referencja LIST-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:LIST-03 -->
```java
static final class Node {
    final int value; Node next;
    Node(int value) { this.value = value; }
}
static Node removeNthFromEnd(Node head, int n) {
    Node dummy = new Node(0); dummy.next = head; Node fast = dummy, slow = dummy;
    for (int i = 0; i < n; i++) { fast = fast.next; }
    while (fast.next != null) { fast = fast.next; slow = slow.next; }
    slow.next = slow.next.next; return dummy.next;
}
```
