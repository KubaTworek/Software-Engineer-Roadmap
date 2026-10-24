package pl.jakubtworek.lld.library;

import java.util.*;

/**
 * FIFO z rangą: AVL porządkuje numery przyjęcia, HashMap wskazuje węzeł osoby.
 * Każdy węzeł pamięta rozmiar i wysokość poddrzewa oraz rodzica. Dzięki temu
 * append, anulowanie i rank mają gwarantowane O(log Q) pracy drzewa.
 * Samo contains ma oczekiwane O(1) mapy hash, a snapshot O(Q).
 * Zwykła lista wiązana nie wystarcza: usunęłaby osobę po uchwycie w O(1),
 * ale nie odpowiedziałaby szybko, czy jej pozycja mieści się w pierwszych F.
 */
final class ReservationQueue {

    private static final class Node {

        final MemberId member;
        final long sequence;
        Node left, right, parent;
        int size = 1, height = 1;

        Node(MemberId member, long sequence) {
            this.member = member;
            this.sequence = sequence;
        }
    }

    private final Map<MemberId, Node> members = new HashMap<>();
    private Node root;
    private long sequence;

    int size() {
        return members.size();
    }

    boolean isEmpty() {
        return members.isEmpty();
    }

    boolean contains(MemberId member) {
        return members.containsKey(member);
    }

    void add(MemberId member) {
        Objects.requireNonNull(member);
        if (contains(member)) {
            throw new IllegalArgumentException("Powtórzony członek kolejki");
        }
        long next = Math.incrementExact(sequence);
        Node node = new Node(member, next);
        root = insert(root, node);
        root.parent = null;
        members.put(member, node);
        sequence = next;
    }

    void remove(MemberId member) {
        Node node = members.remove(member);
        if (node == null) {
            return;
        }
        root = delete(root, node.sequence);
        if (root != null) {
            root.parent = null;
        }
    }

    int rank(MemberId member) {
        Node node = members.get(member);
        if (node == null) {
            return -1;
        }
        // Wchodząc do rodzica z prawej, doliczamy jego lewe poddrzewo
        // i samego rodzica; wejście z lewej niczego nie dodaje do rangi.
        int rank = size(node.left);
        while (node.parent != null) {
            if (node == node.parent.right) {
                rank += 1 + size(node.parent.left);
            }
            node = node.parent;
        }
        return rank;
    }

    List<MemberId> snapshot() {
        var result = new ArrayList<MemberId>(size());
        // Jawny stos trzyma referencje bez ogniwa LinkedList na każdy krok.
        // Kopia wyniku odcina widok od późniejszych rotacji mutowalnego AVL.
        var stack = new ArrayDeque<Node>();
        Node node = root;
        while (node != null || !stack.isEmpty()) {
            while (node != null) {
                stack.push(node);
                node = node.left;
            }
            node = stack.pop();
            result.add(node.member);
            node = node.right;
        }
        return List.copyOf(result);
    }

    int indexHeight() {
        return height(root);
    }

    private static Node insert(Node node, Node added) {
        if (node == null) {
            return added;
        }
        if (added.sequence < node.sequence) {
            node.left = insert(node.left, added);
        } else {
            node.right = insert(node.right, added);
        }
        return balance(node);
    }

    private static Node delete(Node node, long sequence) {
        if (sequence < node.sequence) {
            node.left = delete(node.left, sequence);
        } else if (sequence > node.sequence) {
            node.right = delete(node.right, sequence);
        } else {
            if (node.left == null) {
                return node.right;
            }
            if (node.right == null) {
                return node.left;
            }
            // Przeszczepiamy rzeczywisty węzeł następnika, zamiast kopiować
            // MemberId: mapa nadal musi prowadzić do TEGO SAMEGO węzła osoby.
            Node successor = node.right;
            while (successor.left != null) {
                successor = successor.left;
            }
            Node right = delete(node.right, successor.sequence);
            successor.left = node.left;
            successor.right = right;
            node = successor;
        }
        return balance(node);
    }

    private static Node balance(Node node) {
        update(node);
        int delta = height(node.left) - height(node.right);
        if (delta > 1) {
            if (height(node.left.left) < height(node.left.right)) {
                node.left = rotateLeft(node.left);
            }
            return rotateRight(node);
        }
        if (delta < -1) {
            if (height(node.right.right) < height(node.right.left)) {
                node.right = rotateRight(node.right);
            }
            return rotateLeft(node);
        }
        return node;
    }

    private static Node rotateLeft(Node old) {
        Node next = old.right;
        old.right = next.left;
        next.left = old;
        // Najpierw dawny korzeń: metadane nowego rodzica zależą od już
        // poprawnych rozmiarów i wysokości jego dzieci.
        update(old);
        update(next);
        return next;
    }

    private static Node rotateRight(Node old) {
        Node next = old.left;
        old.left = next.right;
        next.right = old;
        // Najpierw dawny korzeń: metadane nowego rodzica zależą od już
        // poprawnych rozmiarów i wysokości jego dzieci.
        update(old);
        update(next);
        return next;
    }

    private static int size(Node node) {
        return node == null ? 0 : node.size;
    }

    private static int height(Node node) {
        return node == null ? 0 : node.height;
    }

    private static void update(Node node) {
        node.size = 1 + size(node.left) + size(node.right);
        node.height = 1 + Math.max(height(node.left), height(node.right));
        if (node.left != null) {
            node.left.parent = node;
        }
        if (node.right != null) {
            node.right.parent = node;
        }
    }
}
