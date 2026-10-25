# Kontrola testów — 22-stack-queue

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja SQ-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:SQ-01 -->
```java
static boolean validBrackets(String text) {
    Deque<Character> stack = new ArrayDeque<>();
    for (int i = 0; i < text.length(); i++) {
        char symbol = text.charAt(i);
        if (symbol == '(' || symbol == '[' || symbol == '{') { stack.push(symbol); }
        else {
            if (stack.isEmpty()) { return false; }
            char open = stack.pop();
            if ((symbol == ')' && open != '(') || (symbol == ']' && open != '[') || (symbol == '}' && open != '{')) { return false; }
        }
    }
    return stack.isEmpty();
}
```

## Referencja SQ-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:SQ-02 -->
```java
static int evaluateRpn(String[] tokens) {
    Deque<Integer> stack = new ArrayDeque<>();
    for (String token : tokens) {
        if (token.equals("+") || token.equals("-") || token.equals("*") || token.equals("/")) {
            int right = stack.pop(), left = stack.pop();
            stack.push(switch (token) { case "+" -> left+right; case "-" -> left-right; case "*" -> left*right; default -> left/right; });
        } else { stack.push(Integer.parseInt(token)); }
    }
    return stack.pop();
}
```

### Mutacja odwrocone-odejmowanie

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:SQ-02:odwrocone-odejmowanie -->
```java
static int evaluateRpn(String[] tokens) {
    Deque<Integer> stack = new ArrayDeque<>();
    for (String token : tokens) {
        if (token.equals("+") || token.equals("-") || token.equals("*") || token.equals("/")) {
            int right = stack.pop(), left = stack.pop();
            stack.push(switch (token) { case "+" -> left+right; case "-" -> right-left; case "*" -> left*right; default -> left/right; });
        } else { stack.push(Integer.parseInt(token)); }
    }
    return stack.pop();
}
```

## Referencja SQ-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:SQ-03 -->
```java
static int[] queueScript(String[] ops, int[] args) {
    Deque<Integer> in = new ArrayDeque<>(), out = new ArrayDeque<>(); List<Integer> reads = new ArrayList<>();
    for (int i = 0; i < ops.length; i++) {
        if (ops[i].equals("add")) { in.push(args[i]); }
        else {
            if (out.isEmpty()) { while (!in.isEmpty()) { out.push(in.pop()); } }
            reads.add(ops[i].equals("peek") ? out.getFirst() : out.pop());
        }
    }
    return reads.stream().mapToInt(Integer::intValue).toArray();
}
```
