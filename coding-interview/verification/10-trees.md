# Kontrola testów — 10-trees

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja TREE-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:TREE-01 -->
```java
static final class Node {
    final int value;
    Node left, right;
    Node(int value) { this.value = value; }
}

static List<Integer> inorder(Node root) {
    List<Integer> result = new ArrayList<>(); Deque<Node> stack = new ArrayDeque<>(); Node current = root;
    while (current != null || !stack.isEmpty()) {
        while (current != null) { stack.push(current); current = current.left; }
        current = stack.pop(); result.add(current.value); current = current.right;
    }
    return result;
}
```

## Referencja TREE-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:TREE-02 -->
```java
static final class Node {
    Node left, right;
}

static boolean isBalanced(Node root) {
    if (root == null) { return true; }
    Deque<Node> pending = new ArrayDeque<>(), order = new ArrayDeque<>(); pending.push(root);
    while (!pending.isEmpty()) { Node n = pending.pop(); order.push(n); if (n.left != null) { pending.push(n.left); } if (n.right != null) { pending.push(n.right); } }
    Map<Node,Integer> heights = new IdentityHashMap<>();
    while (!order.isEmpty()) {
        Node n = order.pop(); int a = heights.getOrDefault(n.left,0), b = heights.getOrDefault(n.right,0);
        if (Math.abs(a-b) > 1) { return false; }
        heights.put(n,1+Math.max(a,b));
    }
    return true;
}
```

## Referencja TREE-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:TREE-03 -->
```java
static final class Node {
    Node left, right;
}

static int diameterEdges(Node root) {
    if (root == null) { return 0; }
    Deque<Node> pending = new ArrayDeque<>(), order = new ArrayDeque<>(); pending.push(root);
    while (!pending.isEmpty()) { Node n = pending.pop(); order.push(n); if (n.left != null) { pending.push(n.left); } if (n.right != null) { pending.push(n.right); } }
    Map<Node,Integer> heights = new IdentityHashMap<>(); int best = 0;
    while (!order.isEmpty()) {
        Node n = order.pop(); int a = heights.getOrDefault(n.left,0), b = heights.getOrDefault(n.right,0);
        best = Math.max(best,a+b); heights.put(n,1+Math.max(a,b));
    }
    return best;
}
```

### Mutacja wezly-zamiast-krawedzi

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:TREE-03:wezly-zamiast-krawedzi -->
```java
static final class Node {
    Node left, right;
}

static int diameterEdges(Node root) {
    if (root == null) { return 0; }
    Deque<Node> pending = new ArrayDeque<>(), order = new ArrayDeque<>(); pending.push(root);
    while (!pending.isEmpty()) { Node n = pending.pop(); order.push(n); if (n.left != null) { pending.push(n.left); } if (n.right != null) { pending.push(n.right); } }
    Map<Node,Integer> heights = new IdentityHashMap<>(); int best = 0;
    while (!order.isEmpty()) {
        Node n = order.pop(); int a = heights.getOrDefault(n.left,0), b = heights.getOrDefault(n.right,0);
        best = Math.max(best,a+b+1); heights.put(n,1+Math.max(a,b));
    }
    return best;
}
```
