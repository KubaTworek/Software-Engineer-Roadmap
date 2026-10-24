package pl.jakubtworek.lld.editor;

import java.util.Objects;

/**
 * Niemutowalny rope: tekst jest drzewem AVL krótkich fragmentów, nie pojedynczym
 * String kopiowanym przy każdej edycji. Węzły pamiętają długość poddrzewa.
 * Dzięki temu pozycja znaku kieruje nas do lewej albo prawej gałęzi.
 * Split i join kopiują drogi, a pozostałe węzły są wspólne ze starymi wersjami.
 * Jednostką indeksowania jest UTF-16 char, tak jak w String; nie grafem użytkownika.
 */
public final class RopeText implements CharSequence {

    static final int CHUNK = 256;

    private static final class Node {

        // nie-null wyłącznie dla liścia
        final String text;
        final Node left, right;
        final int length, height, leaves;

        Node(String text) {
            this.text = text;
            left = right = null;
            length = text.length();
            height = leaves = 1;
        }

        Node(Node left, Node right) {
            this.left = left;
            this.right = right;
            text = null;
            length = Math.addExact(left.length, right.length);
            height = 1 + Math.max(left.height, right.height);
            leaves = Math.addExact(left.leaves, right.leaves);
        }
    }

    private record Split(Node left, Node right) {
    }

    private final Node root;

    private RopeText(Node root) {
        this.root = root;
    }

    public static RopeText of(String text) {
        Objects.requireNonNull(text);
        return new RopeText(build(text, 0, text.length()));
    }

    private static Node build(String text, int start, int end) {
        if (start == end) {
            return null;
        }
        if (end - start <= CHUNK) {
            return new Node(text.substring(start, end));
        }
        int middle = start + (end - start) / 2;
        return new Node(build(text, start, middle), build(text, middle, end));
    }

    @Override
    public int length() {
        return length(root);
    }

    @Override
    public char charAt(int index) {
        Objects.checkIndex(index, length());
        Node node = root;
        while (node.text == null) {
            if (index < node.left.length) {
                node = node.left;
            } else {
                index -= node.left.length;
                node = node.right;
            }
        }
        return node.text.charAt(index);
    }

    @Override
    public RopeText subSequence(int start, int end) {
        Objects.checkFromToIndex(start, end, length());
        Split first = split(root, start);
        return new RopeText(split(first.right, end - start).left);
    }

    /**
     * Zastępujemy zakres offset..offset+removed. Walidacja i całe składanie
     * odbywają się lokalnie. Wyjątek nie może zmienić wejściowego korzenia.
     * Nowy tekst budujemy raz w O(M); nie spłaszczamy N znaków starego dokumentu.
     */
    public RopeText replace(int offset, int removed, String inserted) {
        Objects.requireNonNull(inserted);
        Objects.checkFromIndexSize(offset, removed, length());
        Math.addExact(length() - removed, inserted.length());
        if (removed == 0 && inserted.isEmpty()) {
            return this;
        }
        Split prefix = split(root, offset);
        Split suffix = split(prefix.right, removed);
        return new RopeText(join(join(prefix.left, build(inserted, 0, inserted.length())), suffix.right));
    }

    private static Split split(Node node, int offset) {
        if (offset == 0) {
            return new Split(null, node);
        }
        if (offset == length(node)) {
            return new Split(node, null);
        }
        if (node.text != null) {
            // Cięty liść tworzy fragmenty String; nie zakładamy darmowego
            // widoku substring. Niezmienione poddrzewa nadal współdzielą węzły.
            return new Split(new Node(node.text.substring(0, offset)), new Node(node.text.substring(offset)));
        }
        if (offset < node.left.length) {
            Split part = split(node.left, offset);
            return new Split(part.left, join(part.right, node.right));
        }
        Split part = split(node.right, offset - node.left.length);
        return new Split(join(node.left, part.left), part.right);
    }

    /**
     * Join dwóch uporządkowanych fragmentów schodzi po wyższej stronie.
     * Samo new Node(left,right) mogłoby po serii dopisań stworzyć łańcuch.
     * Łączenie i rotacje zachowują |height(left)-height(right)| <= 1.
     */
    private static Node join(Node left, Node right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        if (left.text != null && right.text != null && left.length + right.length <= CHUNK) {
            return new Node(left.text + right.text);
        }
        if (height(left) > height(right) + 1) {
            return balance(new Node(left.left, join(left.right, right)));
        }
        if (height(right) > height(left) + 1) {
            return balance(new Node(join(left, right.left), right.right));
        }
        return new Node(left, right);
    }

    private static Node balance(Node node) {
        int delta = height(node.left) - height(node.right);
        if (delta > 1) {
            Node left = node.left;
            if (height(left.left) < height(left.right)) {
                left = rotateLeft(left);
            }
            return rotateRight(new Node(left, node.right));
        }
        if (delta < -1) {
            Node right = node.right;
            if (height(right.right) < height(right.left)) {
                right = rotateRight(right);
            }
            return rotateLeft(new Node(node.left, right));
        }
        return node;
    }

    private static Node rotateLeft(Node old) {
        Node next = old.right;
        return new Node(new Node(old.left, next.left), next.right);
    }

    private static Node rotateRight(Node old) {
        Node next = old.left;
        return new Node(next.left, new Node(next.right, old.right));
    }

    private static int length(Node node) {
        return node == null ? 0 : node.length;
    }

    private static int height(Node node) {
        return node == null ? 0 : node.height;
    }

    /**
     * Pełna materializacja jest jawnie O(N), potrzebna dopiero przy wyświetleniu.
     */
    @Override
    public String toString() {
        // Znamy długość wyniku, więc od razu rezerwujemy bufor. Materializacja
        // nadal wymaga odczytu N znaków, mimo taniego wyboru korzenia wersji.
        var text = new StringBuilder(length());
        append(root, text);
        return text.toString();
    }

    private static void append(Node node, StringBuilder text) {
        if (node == null) {
            return;
        }
        if (node.text != null) {
            text.append(node.text);
        } else {
            append(node.left, text);
            append(node.right, text);
        }
    }

    int height() {
        return height(root);
    }

    int leaves() {
        return root == null ? 0 : root.leaves;
    }

    /**
     * Kontrola strukturalna do eksperymentów/testów, nie element ścieżki edycji.
     */
    boolean balanced() {
        return validate(root);
    }

    private static boolean validate(Node node) {
        if (node == null) {
            return true;
        }
        if (node.text != null) {
            return node.length > 0 && node.length <= CHUNK && node.height == 1;
        }
        return Math.abs(height(node.left) - height(node.right)) <= 1
                && node.length == node.left.length + node.right.length && node.height == 1 + Math.max(height(node.left),
                height(node.right)) && validate(node.left) && validate(node.right);
    }
}
