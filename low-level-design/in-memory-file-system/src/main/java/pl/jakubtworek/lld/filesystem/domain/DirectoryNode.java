package pl.jakubtworek.lld.filesystem.domain;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Trwałe drzewo AVL dzieci katalogu. Zmiana kopiuje O(log B) węzłów zamiast
 * całej mapy B rodzeństw. Wysokość jest gwarantowana rotacjami, nie losowaniem.
 * Adapter NavigableMap budujemy leniwie tylko dla publicznego odczytu children().
 * Operacje systemu plików korzystają bezpośrednio z drzewa.
 */
public final class DirectoryNode implements FsNode {

    private record Node(String name, FsNode value, Node left, Node right, int height, int size) {
    }

    private final Node root;
    private NavigableMap<String, FsNode> view;

    public DirectoryNode(NavigableMap<String, FsNode> children) {
        Node tree = null;
        for (var entry : children.entrySet()) {
            FsPath.validateName(entry.getKey());
            tree = put(tree, entry.getKey(), Objects.requireNonNull(entry.getValue()));
        }
        root = tree;
    }

    private DirectoryNode(Node root) {
        this.root = root;
    }

    public static DirectoryNode empty() {
        return new DirectoryNode((Node) null);
    }

    // Wrapper blokuje zapis, ale sam nie kopiuje. Jest stabilny, bo mapę
    // bazową budujemy prywatnie i nigdy później nie zmieniamy jej zawartości.
    // Pole view jest leniwe, bez synchronizacji; logiczna stałość to nie lock.
    public NavigableMap<String, FsNode> children() {
        if (view == null) {
            var result = new TreeMap<String, FsNode>();
            forEachChild(result::put);
            view = Collections.unmodifiableNavigableMap(result);
        }
        return view;
    }

    FsNode child(String name) {
        Node node = root;
        while (node != null) {
            int comparison = name.compareTo(node.name);
            if (comparison == 0) {
                return node.value;
            }
            node = comparison < 0 ? node.left : node.right;
        }
        return null;
    }

    boolean isEmpty() {
        return root == null;
    }

    void forEachChild(BiConsumer<String, FsNode> action) {
        var stack = new ArrayDeque<Node>();
        Node node = root;
        while (node != null || !stack.isEmpty()) {
            while (node != null) {
                stack.push(node);
                node = node.left;
            }
            node = stack.pop();
            action.accept(node.name, node.value);
            node = node.right;
        }
    }

    DirectoryNode with(String name, FsNode node) {
        FsPath.validateName(name);
        return new DirectoryNode(put(root, name, Objects.requireNonNull(node)));
    }

    DirectoryNode without(String name) {
        return new DirectoryNode(remove(root, name));
    }

    private static int height(Node node) {
        return node == null ? 0 : node.height;
    }

    private static int size(Node node) {
        return node == null ? 0 : node.size;
    }

    private static Node node(String name, FsNode value, Node left, Node right) {
        return new Node(name, value, left, right, 1 + Math.max(height(left), height(right)),
                1 + size(left) + size(right));
    }

    private static Node left(Node x) {
        Node y = x.right;
        return node(y.name, y.value, node(x.name, x.value, x.left, y.left), y.right);
    }

    private static Node right(Node y) {
        Node x = y.left;
        return node(x.name, x.value, x.left, node(y.name, y.value, x.right, y.right));
    }

    private static Node balance(Node n) {
        int delta = height(n.left) - height(n.right);
        if (delta > 1) {
            if (height(n.left.left) < height(n.left.right)) {
                n = node(n.name, n.value, left(n.left), n.right);
            }
            return right(n);
        }
        if (delta < -1) {
            if (height(n.right.right) < height(n.right.left)) {
                n = node(n.name, n.value, n.left, right(n.right));
            }
            return left(n);
        }
        return n;
    }

    private static Node put(Node n, String key, FsNode value) {
        if (n == null) {
            return node(key, value, null, null);
        }
        int comparison = key.compareTo(n.name);
        if (comparison == 0) {
            return node(key, value, n.left, n.right);
        }
        return balance(comparison < 0 ? node(n.name, n.value, put(n.left, key, value), n.right) : node(n.name, n.value,
                n.left, put(n.right, key, value)));
    }

    private static Node remove(Node n, String key) {
        if (n == null) {
            return null;
        }
        int comparison = key.compareTo(n.name);
        if (comparison < 0) {
            return balance(node(n.name, n.value, remove(n.left, key), n.right));
        }
        if (comparison > 0) {
            return balance(node(n.name, n.value, n.left, remove(n.right, key)));
        }
        if (n.left == null) {
            return n.right;
        }
        if (n.right == null) {
            return n.left;
        }
        Node successor = n.right;
        while (successor.left != null) {
            successor = successor.left;
        }
        return balance(node(successor.name, successor.value, n.left, remove(n.right, successor.name)));
    }

    int indexHeight() {
        return height(root);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DirectoryNode directory && children().equals(directory.children());
    }

    @Override
    public int hashCode() {
        return children().hashCode();
    }

    @Override
    public String toString() {
        return "DirectoryNode[children=" + children() + "]";
    }
}
