# Kontrola testów — 23-bst

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja BST-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:BST-01 -->
```java
static final class Node {
    final int value; Node left, right;
    Node(int value) { this.value=value; }
}
static boolean isStrictBst(Node root) {
    record Frame(Node node, long lower, long upper) {}
    Deque<Frame> stack = new ArrayDeque<>(); if (root != null) { stack.push(new Frame(root,Long.MIN_VALUE,Long.MAX_VALUE)); }
    while (!stack.isEmpty()) {
        Frame frame = stack.pop(); Node node = frame.node();
        if (node.value <= frame.lower() || node.value >= frame.upper()) { return false; }
        if (node.left != null) { stack.push(new Frame(node.left,frame.lower(),node.value)); }
        if (node.right != null) { stack.push(new Frame(node.right,node.value,frame.upper())); }
    }
    return true;
}
```

### Mutacja duplikaty-dozwolone

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:BST-01:duplikaty-dozwolone -->
```java
static final class Node {
    final int value; Node left, right;
    Node(int value) { this.value=value; }
}
static boolean isStrictBst(Node root) {
    record Frame(Node node, long lower, long upper) {}
    Deque<Frame> stack = new ArrayDeque<>(); if (root != null) { stack.push(new Frame(root,Long.MIN_VALUE,Long.MAX_VALUE)); }
    while (!stack.isEmpty()) {
        Frame frame = stack.pop(); Node node = frame.node();
        if (node.value < frame.lower() || node.value > frame.upper()) { return false; }
        if (node.left != null) { stack.push(new Frame(node.left,frame.lower(),node.value)); }
        if (node.right != null) { stack.push(new Frame(node.right,node.value,frame.upper())); }
    }
    return true;
}
```

## Referencja BST-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:BST-02 -->
```java
static final class Node {
    final int value; Node left, right;
    Node(int value) { this.value=value; }
}
static int kthSmallest(Node root, int k) {
    Deque<Node> stack = new ArrayDeque<>(); Node current = root;
    while (current != null || !stack.isEmpty()) {
        while (current != null) { stack.push(current); current = current.left; }
        current = stack.pop(); if (--k == 0) { return current.value; } current = current.right;
    }
    throw new IllegalArgumentException("k poza kontraktem");
}
```

## Referencja BST-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:BST-03 -->
```java
static final class Node {
    final int value; Node left, right;
    Node(int value) { this.value=value; }
}
static long rangeSum(Node root, int low, int high) {
    Deque<Node> stack = new ArrayDeque<>(); if (root != null) { stack.push(root); } long sum = 0;
    while (!stack.isEmpty()) {
        Node node = stack.pop(); if (node.value >= low && node.value <= high) { sum += node.value; }
        if (node.left != null && node.value > low) { stack.push(node.left); }
        if (node.right != null && node.value < high) { stack.push(node.right); }
    }
    return sum;
}
```
