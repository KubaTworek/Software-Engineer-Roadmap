package pl.jakubtworek.lld.workflow.domain;

import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Trwały wektor binarny: dopisanie kopiuje jedynie ścieżkę O(log N).
 * Wszystkie wcześniejsze wersje zachowują swoje korzenie. Odczyt indeksu
 * kosztuje O(log N), iteracja O(N), dodatkowa pamięć na dopisanie O(log N).
 * Typ listy przechowuje certyfikat ciągłości, dzięki czemu snapshot nie
 * sprawdza ponownie całej zaakceptowanej historii przy każdej komendzie.
 */
final class HistoryVector<E> extends AbstractList<E> {

    private record Node(Node left, Node right, Object value) {
    }

    private final Node root;
    private final int count, depth;

    // null = zdarzenia; nie-null = rewizje tego autora.
    private final UserId author;

    private HistoryVector(Node root, int count, int depth, UserId author) {
        this.root = root;
        this.count = count;
        this.depth = depth;
        this.author = author;
    }

    static java.util.List<WorkflowEvent> events(java.util.List<WorkflowEvent> source) {
        if (source instanceof HistoryVector<?> vector && vector.author == null) {
            return source;
        }
        HistoryVector<WorkflowEvent> result = new HistoryVector<>(null, 0, 0, null);
        for (WorkflowEvent event : source) {
            result = result.append(event);
        }
        return result;
    }

    static java.util.List<ContentRevision> revisions(java.util.List<ContentRevision> source, UserId author) {
        if (source instanceof HistoryVector<?> vector && author.equals(vector.author)) {
            return source;
        }
        HistoryVector<ContentRevision> result = new HistoryVector<>(null, 0, 0, author);
        for (ContentRevision revision : source) {
            result = result.append(revision);
        }
        return result;
    }

    static <T> java.util.List<T> append(java.util.List<T> source, T value) {
        if (!(source instanceof HistoryVector<T> vector)) {
            throw new IllegalArgumentException("Brak zweryfikowanego prefiksu");
        }
        return vector.append(value);
    }

    private HistoryVector<E> append(E value) {
        Objects.requireNonNull(value);
        long sequence;
        if (author == null && value instanceof WorkflowEvent event) {
            sequence = event.sequence();
        } else if (author != null && value instanceof ContentRevision revision && author.equals(revision.editor())) {
            sequence = revision.number();
        } else {
            throw new IllegalArgumentException("Niepoprawny element historii");
        }
        if (sequence != (long) count + 1) {
            throw new IllegalArgumentException("Nieciągła sekwencja historii");
        }
        int nextCount = Math.incrementExact(count);
        Node tree = root;
        int levels = depth;
        if (count > 0 && (long) count == (1L << depth)) {
            tree = new Node(tree, null, null);
            levels++;
        }
        return new HistoryVector<>(put(tree, levels, count, value), nextCount, levels, author);
    }

    private static Node put(Node node, int depth, int index, Object value) {
        if (depth == 0) {
            return new Node(null, null, value);
        }
        Node left = node == null ? null : node.left, right = node == null ? null : node.right;
        if ((index & (1 << (depth - 1))) == 0) {
            left = put(left, depth - 1, index, value);
        } else {
            right = put(right, depth - 1, index, value);
        }
        return new Node(left, right, null);
    }

    @Override
    public int size() {
        return count;
    }

    @Override
    @SuppressWarnings("unchecked")
    public E get(int index) {
        Objects.checkIndex(index, count);
        Node node = root;
        for (int level = depth - 1; level >= 0; level--) {
            node = (index & (1 << level)) == 0 ? node.left : node.right;
        }
        // Erasure nie sprawdza E w węźle Object. Bezpieczeństwo rzutowania
        // wynika z kontrolowanej konstrukcji i walidacji elementów przy append.
        return (E) node.value;
    }

    @Override
    public Iterator<E> iterator() {
        return new Iterator<>() {

            final ArrayDeque<Node> pending = initialize();

            private ArrayDeque<Node> initialize() {
                var stack = new ArrayDeque<Node>();
                if (root != null) {
                    stack.push(root);
                }
                return stack;
            }

            public boolean hasNext() {
                return !pending.isEmpty();
            }

            @SuppressWarnings("unchecked")
            public E next() {
                while (!pending.isEmpty()) {
                    Node node = pending.pop();
                    if (node.value != null) {
                        // Erasure nie sprawdza E w węźle Object. Bezpieczeństwo rzutowania
                        // wynika z kontrolowanej konstrukcji i walidacji elementów przy append.
                        return (E) node.value;
                    }
                    if (node.right != null) {
                        pending.push(node.right);
                    }
                    if (node.left != null) {
                        pending.push(node.left);
                    }
                }
                throw new NoSuchElementException();
            }
        };
    }

    /**
     * Domyślny listIterator z AbstractList używa get(i), tutaj O(log N).
     * Materializujemy przez własny liniowy iterator, aby equals i pełne
     * przejście listIterator nie powtarzały N wyszukiwań w drzewie.
     */
    @Override
    public java.util.ListIterator<E> listIterator(int index) {
        return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(this)).listIterator(index);
    }
}
