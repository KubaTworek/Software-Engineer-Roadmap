# Kontrola testów — 17-trie

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja TRIE-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:TRIE-01 -->
```java
static boolean[] prefixQueries(String[] words, String[] prefixes) {
    class T { T[] children = new T[26]; boolean terminal; int count; String word; }
    T root = new T();
    for (String word : words) {
        T node = root;
        for (int i = 0; i < word.length(); i++) { int index = word.charAt(i)-'a'; if (node.children[index] == null) { node.children[index] = new T(); } node = node.children[index]; }
        node.terminal = true;
    }
    boolean[] result = new boolean[prefixes.length];
    for (int i = 0; i < prefixes.length; i++) {
        T node = root;
        for (int j = 0; j < prefixes[i].length() && node != null; j++) { node = node.children[prefixes[i].charAt(j)-'a']; }
        result[i] = node != null;
    }
    return result;
}
```

## Referencja TRIE-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:TRIE-02 -->
```java
static int[] distinctPrefixCounts(String[] words, String[] prefixes) {
    class T { T[] children = new T[26]; boolean terminal; int count; String word; }
    T root = new T();
    Set<String> unique = new HashSet<>(Arrays.asList(words));
    for (String word : unique) {
        T node = root; node.count++;
        for (int i = 0; i < word.length(); i++) { int index = word.charAt(i)-'a'; if (node.children[index] == null) { node.children[index] = new T(); } node = node.children[index]; node.count++; }
        node.terminal = true;
    }
    int[] result = new int[prefixes.length];
    for (int i = 0; i < prefixes.length; i++) {
        T node = root; for (int j = 0; j < prefixes[i].length() && node != null; j++) { node = node.children[prefixes[i].charAt(j)-'a']; }
        result[i] = node == null ? 0 : node.count;
    }
    return result;
}
```

### Mutacja liczenie-duplikatow

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:TRIE-02:liczenie-duplikatow -->
```java
static int[] distinctPrefixCounts(String[] words, String[] prefixes) {
    class T { T[] children = new T[26]; boolean terminal; int count; String word; }
    T root = new T();
    List<String> unique = Arrays.asList(words);
    for (String word : unique) {
        T node = root; node.count++;
        for (int i = 0; i < word.length(); i++) { int index = word.charAt(i)-'a'; if (node.children[index] == null) { node.children[index] = new T(); } node = node.children[index]; node.count++; }
        node.terminal = true;
    }
    int[] result = new int[prefixes.length];
    for (int i = 0; i < prefixes.length; i++) {
        T node = root; for (int j = 0; j < prefixes[i].length() && node != null; j++) { node = node.children[prefixes[i].charAt(j)-'a']; }
        result[i] = node == null ? 0 : node.count;
    }
    return result;
}
```

## Referencja TRIE-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:TRIE-03 -->
```java
static String longestBuildable(String[] words) {
    class T { T[] children = new T[26]; boolean terminal; int count; String word; }
    T root = new T();
    for (String word : words) {
        T node = root;
        for (int i = 0; i < word.length(); i++) { int index = word.charAt(i)-'a'; if (node.children[index] == null) { node.children[index] = new T(); } node = node.children[index]; }
        node.terminal = true; node.word = word;
    }
    Deque<T> stack = new ArrayDeque<>(); for (T child : root.children) { if (child != null) { stack.push(child); } }
    String best = "";
    while (!stack.isEmpty()) {
        T node = stack.pop(); if (!node.terminal) { continue; }
        if (node.word.length() > best.length() || (node.word.length() == best.length() && node.word.compareTo(best) < 0)) { best = node.word; }
        for (T child : node.children) { if (child != null) { stack.push(child); } }
    }
    return best;
}
```
