# Kontrola testów — 13-backtracking

[Zasady obszaru weryfikacji](README.md)

Implementacje referencyjne do sprawdzania kontraktów testów. Czytaj po własnej próbie.
Nie są automatycznie wstawiane do banku ani treningów. Importy java.util.* zapewnia skrypt.
Warianty TREE-02 i TREE-03 używają O(n) pamięci na wysokości i porządek, aby uniknąć stosu Javy.

## Referencja BACK-01

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:BACK-01 -->
```java
static List<List<Integer>> combinations(int n, int k) {
    List<List<Integer>> result = new ArrayList<>();
    class Search {
        void run(int start, List<Integer> path) {
            if (path.size() == k) { result.add(new ArrayList<>(path)); return; }
            for (int i = start; i <= n-(k-path.size())+1; i++) { path.add(i); run(i+1,path); path.remove(path.size()-1); }
        }
    }
    new Search().run(1,new ArrayList<>()); return result;
}
```

### Mutacja aliasowanie-sciezki

Celowo błędny wariant; kompilacja musi się udać, a verify() ma zgłosić AssertionError.

<!-- mutant:BACK-01:aliasowanie-sciezki -->
```java
static List<List<Integer>> combinations(int n, int k) {
    List<List<Integer>> result = new ArrayList<>();
    class Search {
        void run(int start, List<Integer> path) {
            if (path.size() == k) { result.add(path); return; }
            for (int i = start; i <= n-(k-path.size())+1; i++) { path.add(i); run(i+1,path); path.remove(path.size()-1); }
        }
    }
    new Search().run(1,new ArrayList<>()); return result;
}
```

## Referencja BACK-02

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:BACK-02 -->
```java
static List<List<Integer>> uniquePermutations(int[] values) {
    int[] sorted = values.clone(); Arrays.sort(sorted); boolean[] used = new boolean[sorted.length];
    List<List<Integer>> result = new ArrayList<>();
    class Search {
        void run(List<Integer> path) {
            if (path.size() == sorted.length) { result.add(new ArrayList<>(path)); return; }
            for (int i = 0; i < sorted.length; i++) {
                if (used[i] || (i > 0 && sorted[i] == sorted[i-1] && !used[i-1])) { continue; }
                used[i] = true; path.add(sorted[i]); run(path); path.remove(path.size()-1); used[i] = false;
            }
        }
    }
    new Search().run(new ArrayList<>()); return result;
}
```

## Referencja BACK-03

Kontekst: pełne składowe klasy, weryfikowane oryginalnym verify().

<!-- reference:BACK-03 -->
```java
static List<String> parentheses(int pairs) {
    List<String> result = new ArrayList<>();
    class Search {
        void run(int open, int closed, StringBuilder path) {
            if (closed == pairs) { result.add(path.toString()); return; }
            if (open < pairs) { path.append('('); run(open+1,closed,path); path.deleteCharAt(path.length()-1); }
            if (closed < open) { path.append(')'); run(open,closed+1,path); path.deleteCharAt(path.length()-1); }
        }
    }
    new Search().run(0,0,new StringBuilder()); return result;
}
```
