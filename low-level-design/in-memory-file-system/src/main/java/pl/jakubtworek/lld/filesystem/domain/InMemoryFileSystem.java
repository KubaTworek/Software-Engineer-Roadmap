package pl.jakubtworek.lld.filesystem.domain;

import java.util.*;
import static pl.jakubtworek.lld.filesystem.domain.FileSystemException.Reason.*;
import static pl.jakubtworek.lld.filesystem.domain.FileSystemException.Operation.*;
import static pl.jakubtworek.lld.filesystem.domain.FileSystemView.failure;

/**
 * Jednowątkowy właściciel reguł między gałęziami. Stanem jest gotowy widok:
 * każda mutacja buduje kandydata, a publish wymienia jedną referencję.
 * Niemutowalność pozwala współdzielić węzły kopii, nie gwarantuje współbieżności.
 */
public final class InMemoryFileSystem {

    private FileSystemView view = new FileSystemView(DirectoryNode.empty());

    public FileSystemView snapshot() {
        return view;
    }

    public FsNode stat(FsPath path) {
        return view.stat(path);
    }

    public String read(FsPath path) {
        return view.read(path);
    }

    public List<FsPath> list(FsPath path) {
        return view.list(path);
    }

    public List<FsPath> find(FsPath start, String fragment) {
        return view.find(start, fragment);
    }

    public List<FsPath> find(FsPath start, SearchQuery query) {
        return view.find(start, query);
    }

    public void mkdir(FsPath path) {
        create(path, DirectoryNode.empty(), MKDIR);
    }

    public void createFile(FsPath path, String text) {
        create(path, new TextFile(text), CREATE_FILE);
    }

    private void create(FsPath path, FsNode node, FileSystemException.Operation op) {
        protectRoot(path, op);
        DirectoryNode parent = view.directory(path.parent(), op, path);
        free(parent, path, op);
        publish(replace(view.root(), path.parent(), parent.with(path.name(), node)));
    }

    public void write(FsPath path, String text) {
        var file = new TextFile(text);
        if (!(view.resolve(path, WRITE, path) instanceof TextFile))
            throw failure(NOT_FILE, WRITE, path, path);
        DirectoryNode parent = view.directory(path.parent(), WRITE, path);
        publish(replace(view.root(), path.parent(), parent.with(path.name(), file)));
    }

    public void delete(FsPath path, DeleteMode mode) {
        Objects.requireNonNull(mode);
        protectRoot(path, DELETE);
        FsNode node = view.resolve(path, DELETE, path);
        if (node instanceof DirectoryNode dir && mode == DeleteMode.EMPTY_ONLY && !dir.isEmpty()) {
            throw failure(DIRECTORY_NOT_EMPTY, DELETE, path, path);
        }
        DirectoryNode parent = view.directory(path.parent(), DELETE, path);
        publish(replace(view.root(), path.parent(), parent.without(path.name())));
    }

    /**
     * Wspólna walidacja move/copy, ale odrębna intencja. Kopia katalogu do potomka
     * jest świadomie zabroniona mimo braku cyklu referencji w niemutowalnym DAG.
     * Zapobiega niejasnemu znaczeniu kopiowania źródła do samego siebie.
     * Nie nadpisujemy ani nie łączymy istniejących katalogów.
     */
    private FsNode transferSource(FsPath source, FsPath target, FileSystemException.Operation op) {
        protectRoot(source, op);
        protectRoot(target, op);
        FsNode node = view.resolve(source, op, source);
        if (source.equals(target) || node instanceof DirectoryNode && source.contains(target)) {
            throw failure(op == MOVE ? INVALID_MOVE : INVALID_COPY, op, source, target);
        }
        DirectoryNode destination = view.directory(target.parent(), op, target);
        free(destination, target, op);
        return node;
    }

    /**
     * Najpierw pełna walidacja, potem odłączenie/przyłączenie lokalnego kandydata.
     * Rodzica celu pobieramy PONOWNIE z kandydata: stara mapa przy rename
     * w tym samym katalogu mogłaby przywrócić źródłową nazwę.
     */
    public void move(FsPath source, FsPath target) {
        FsNode node = transferSource(source, target, MOVE);
        DirectoryNode parent = view.directory(source.parent(), MOVE, source);
        var candidate = new FileSystemView(replace(view.root(), source.parent(), parent.without(source.name())));
        DirectoryNode destination = candidate.directory(target.parent(), MOVE, target);
        publish(replace(candidate.root(), target.parent(), destination.with(target.name(), node)));
    }

    /**
     * Kopia logiczna nie wymaga głębokiego klonowania niezmiennych wartości.
     * Późniejszy write/delete/move buduje własną drogę przodków, zatem żadna
     * operacja na kopii nie modyfikuje źródła. To nie jest mutowalny hard link.
     */
    public void copy(FsPath source, FsPath target) {
        FsNode node = transferSource(source, target, COPY);
        DirectoryNode parent = view.directory(target.parent(), COPY, target);
        publish(replace(view.root(), target.parent(), parent.with(target.name(), node)));
    }

    private void publish(DirectoryNode candidate) {
        view = new FileSystemView(candidate);
    }

    private static void free(DirectoryNode parent, FsPath target, FileSystemException.Operation op) {
        if (parent.child(target.name()) != null)
            throw failure(ALREADY_EXISTS, op, target, target);
    }

    private static void protectRoot(FsPath path, FileSystemException.Operation op) {
        if (path.isRoot())
            throw failure(ROOT_PROTECTED, op, path, path);
    }

    /**
     * Kopiowanie przodków od dołu; bez rekurencji i bez modyfikacji dawnych map.
     * Pozostałe gałęzie oraz teksty współdzielimy jako niemutowalne wartości.
     */
    private static DirectoryNode replace(DirectoryNode tree, FsPath path, DirectoryNode replacement) {
        var ancestors = new ArrayList<DirectoryNode>();
        DirectoryNode current = tree;
        for (String name : path.segments()) {
            ancestors.add(current);
            current = (DirectoryNode) current.child(name);
        }
        DirectoryNode result = replacement;
        for (int i = path.segments().size() - 1; i >= 0; i--) {
            result = ancestors.get(i).with(path.segments().get(i), result);
        }
        return result;
    }
}
