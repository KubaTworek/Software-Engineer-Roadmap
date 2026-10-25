# Kontrola testów — 08-fast-slow

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja FAST-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:FAST-01 -->
```java
static final class Node {
    final int value;
    Node next;
    Node(int value) { this.value = value; }
}

static Node cycleEntry(Node head) {
    Node slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next; fast = fast.next.next;
        if (slow == fast) {
            slow = head;
            while (slow != fast) { slow = slow.next; fast = fast.next; }
            return slow;
        }
    }
    return null;
}
```

## Referencja FAST-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:FAST-02 -->
```java
static final class Node {
    Node next;
}

static int cycleLength(Node head) {
    Node slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next; fast = fast.next.next;
        if (slow == fast) { int count = 1; for (Node p = slow.next; p != slow; p = p.next) { count++; } return count; }
    }
    return 0;
}
```

## Referencja FAST-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:FAST-03 -->
```java
static final class Node {
    Node next;
}

static Node middleNode(Node head) {
    Node slow = head, fast = head;
    while (fast != null && fast.next != null) { slow = slow.next; fast = fast.next.next; }
    return slow;
}
```

### Mutacja lewy-srodek

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:FAST-03:lewy-srodek -->
```java
static final class Node {
    Node next;
}

static Node middleNode(Node head) {
    Node slow = head, fast = head;
    while (fast != null && fast.next != null && fast.next.next != null) { slow = slow.next; fast = fast.next.next; }
    return slow;
}
```
