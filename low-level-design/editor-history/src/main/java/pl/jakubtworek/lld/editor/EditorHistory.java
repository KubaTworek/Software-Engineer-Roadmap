package pl.jakubtworek.lld.editor;

import java.util.*;

/**
 * Drzewo wersji z bezpośrednimi odnośnikami parent/children. Undo nie wykonuje
 * odwrotnej operacji na znakach: wybiera zachowany niemutowalny korzeń.
 * Nowa edycja po undo zachowuje dawną gałąź redo. Mapa ID daje skok średnio O(1).
 * Kontrakt jednowątkowy; expectedVersion chroni intencję, nie synchronizuje wątków.
 */
public final class EditorHistory {

    private static final class Version {

        final EditorSnapshot snapshot;
        final Version parent;

        // Tekst wersji jest stały, topologia rośnie. final nie zamraża mapy;
        // LinkedHashMap zachowuje kolejność gałęzi, a branches() kopiuje opisy.
        final LinkedHashMap<Long, Version> children = new LinkedHashMap<>();
        Version preferred;

        Version(EditorSnapshot snapshot, Version parent) {
            this.snapshot = snapshot;
            this.parent = parent;
        }
    }

    private final Map<Long, Version> versions = new HashMap<>();
    private Version current;
    private long sequence;

    public EditorHistory(String initialText) {
        current = new Version(new EditorSnapshot(0, RopeText.of(initialText)), null);
        versions.put(0L, current);
    }

    public EditorSnapshot snapshot() {
        return current.snapshot;
    }

    public int versionCount() {
        return versions.size();
    }

    /**
     * Jedna ścieżka edycji. Najpierw sprawdzamy wersję i budujemy nowy rope,
     * potem dopisujemy węzeł i zmieniamy kursor. Błąd zakresu ani przepełnienie
     * numeru wersji nie gubią aktualnej gałęzi. Każda niepusta intencja tworzy
     * wersję, także zastąpienie tekstu identycznym tekstem.
     */
    public EditorSnapshot replace(long expectedVersion, int offset, int removed, String inserted) {
        // To walidacja intencji, nie CAS: sprawdzenie i późniejszy zapis
        // są oddzielnymi krokami, bez ochrony dwóch równoległych edycji.
        if (expectedVersion != snapshot().version()) {
            throw new IllegalStateException("Nieaktualna wersja edycji");
        }
        RopeText text = snapshot().text().replace(offset, removed, inserted);
        if (removed == 0 && inserted.isEmpty()) {
            return snapshot();
        }
        long nextId = Math.incrementExact(sequence);
        var next = new Version(new EditorSnapshot(nextId, text), current);
        versions.put(nextId, next);
        current.children.put(nextId, next);
        current.preferred = next;
        current = next;
        sequence = nextId;
        return snapshot();
    }

    public EditorSnapshot insert(int offset, String text) {
        return replace(snapshot().version(), offset, 0, text);
    }

    public EditorSnapshot delete(int offset, int length) {
        return replace(snapshot().version(), offset, length, "");
    }

    public EditorSnapshot undo() {
        if (current.parent == null) {
            throw new NoSuchElementException("Brak poprzedniej wersji");
        }
        Version previous = current;
        current = current.parent;
        current.preferred = previous;
        return snapshot();
    }

    /**
     * Domyślnie wracamy ostatnio odwiedzoną/utworzoną gałęzią.
     */
    public EditorSnapshot redo() {
        if (current.preferred == null) {
            throw new NoSuchElementException("Brak gałęzi redo");
        }
        return redo(current.preferred.snapshot.version());
    }

    public EditorSnapshot redo(long childVersion) {
        Version child = current.children.get(childVersion);
        if (child == null) {
            throw new IllegalArgumentException("Wersja nie jest bezpośrednim potomkiem");
        }
        current.preferred = child;
        current = child;
        return snapshot();
    }

    public EditorSnapshot checkout(long version) {
        Version selected = versions.get(version);
        if (selected == null) {
            throw new IllegalArgumentException("Nieznana wersja");
        }
        current = selected;
        return snapshot();
    }

    /**
     * O(B) dla B gałęzi bieżącej wersji, bez kosztu długości dokumentów.
     */
    public List<RevisionInfo> branches() {
        return current.children.values().stream().map(node -> new RevisionInfo(node.snapshot.version(),
                OptionalLong.of(current.snapshot.version()), node.snapshot.text().length())).toList();
    }
}
