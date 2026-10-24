package pl.jakubtworek.lld.filesystem.domain;

import java.util.*;
import static pl.jakubtworek.lld.filesystem.domain.FileSystemException.Reason.*;
import static pl.jakubtworek.lld.filesystem.domain.FileSystemException.Operation.*;

/**
 * Odczyt przypięty do jednego niemutowalnego korzenia. Nie odwołuje się do
 * bieżącego systemu, więc read/list/find dawnego widoku zachowują dawne wyniki.
 * Ten sam kod odczytu obsługuje aktualny stan; unikamy dwóch kopii reguł resolve.
 */
public final class FileSystemView {

    private final DirectoryNode root;

    FileSystemView(DirectoryNode root) {
        this.root = Objects.requireNonNull(root);
    }

    public DirectoryNode root() {
        return root;
    }

    public FsNode stat(FsPath path) {
        return resolve(path, STAT, path);
    }

    public String read(FsPath path) {
        FsNode node = resolve(path, READ, path);
        if (node instanceof TextFile file)
            return file.content();
        throw failure(NOT_FILE, READ, path, path);
    }

    public List<FsPath> list(FsPath path) {
        var result = new ArrayList<FsPath>();
        directory(path, LIST, path).forEachChild((name, node) -> result.add(path.child(name)));
        return List.copyOf(result);
    }

    public List<FsPath> find(FsPath start, String fragment) {
        return find(start, SearchQuery.unlimited(SearchCriteria.nameContains(fragment)));
    }

    /**
     * Stos jawny chroni stos wywołań. Głębokość liczymy od startu, nie korzenia.
     * Niespełnione kryterium katalogu nie odcina jego potomków. Limit głębokości
     * jest sprawdzany przed dodaniem dzieci, więc nie odwiedzamy zbędnych gałęzi.
     */
    public List<FsPath> find(FsPath start, SearchQuery query) {
        Objects.requireNonNull(query);
        record Visit(FsPath path, FsNode node, int depth) {
        }
        // Jawny stos przechowuje ramki przejścia na stercie zamiast na stosie
        // wywołań JVM. Głęboka hierarchia nie wymaga rekursji po katalogach.
        var pending = new ArrayDeque<Visit>();
        pending.push(new Visit(start, resolve(start, FIND, start), 0));
        var found = new ArrayList<FsPath>();
        while (!pending.isEmpty()) {
            var current = pending.pop();
            if (query.criteria().matches(current.path(), current.node()))
                found.add(current.path());
            // Odcinamy dodawanie dzieci, nie tylko filtrujemy gotowy wynik.
            // Niedopasowana nazwa rodzica sama nie odcina jego potomków.
            if (current.depth() < query.maxDepth() && current.node() instanceof DirectoryNode dir) {
                dir.forEachChild((name, node) -> pending.push(new Visit(current.path().child(name), node,
                        current.depth() + 1)));
            }
        }
        return found.stream().sorted().toList();
    }

    /**
     * Kontekst operacji zachowujemy także, gdy rozwiązujemy tylko jej rodzica.
     */
    FsNode resolve(FsPath lookup, FileSystemException.Operation operation, FsPath requested) {
        Objects.requireNonNull(lookup);
        FsNode current = root;
        // Prefiks diagnostyczny budujemy dopiero przy odmowie. Na poprawnej
        // drodze nie kopiujemy list długości 1,2,...,D (dawny koszt O(D²)).
        int depth = 0;
        for (String name : lookup.segments()) {
            if (!(current instanceof DirectoryNode dir)) {
                throw failure(NOT_DIRECTORY, operation, requested, new FsPath(lookup.segments().subList(0, depth)));
            }
            depth++;
            current = dir.child(name);
            if (current == null) {
                throw failure(NOT_FOUND, operation, requested, new FsPath(lookup.segments().subList(0, depth)));
            }
        }
        return current;
    }

    DirectoryNode directory(FsPath lookup, FileSystemException.Operation operation, FsPath requested) {
        if (resolve(lookup, operation, requested) instanceof DirectoryNode dir)
            return dir;
        throw failure(NOT_DIRECTORY, operation, requested, lookup);
    }

    static FileSystemException failure(FileSystemException.Reason reason, FileSystemException.Operation op, FsPath path,
            FsPath problem) {
        return new FileSystemException(reason, op, path, problem);
    }
}
