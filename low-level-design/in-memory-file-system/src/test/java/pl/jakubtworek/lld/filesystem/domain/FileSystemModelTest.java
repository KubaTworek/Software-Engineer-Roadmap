package pl.jakubtworek.lld.filesystem.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.filesystem.domain.InMemoryFileSystemTest.p;
import static pl.jakubtworek.lld.filesystem.domain.FileSystemException.Reason.*;
import static pl.jakubtworek.lld.filesystem.domain.FileSystemException.Operation.*;

/**
 * Niezależna wyrocznia to płaska mapa pełnych ścieżek: null oznacza katalog.
 * Model przenosi i kopiuje przez zamianę prefiksów, a SUT kopiuje przodków drzewa.
 * Nie używamy resolve, contains ani wyszukiwania SUT do obliczenia oczekiwań.
 * Ziarno i numer kroku pozwalają odtworzyć skończoną próbkę, nie dowodzą wszystkich historii.
 */
class FileSystemModelTest {

    record Command(FileSystemException.Operation op, String path, String target, String text, boolean recursive,
            int depth) {
    }

    record Frozen(FileSystemView view, Map<String, String> entries) {
    }

    static final class Refusal extends RuntimeException {

        final FileSystemException.Reason reason;
        final String path, problem;

        Refusal(FileSystemException.Reason reason, String path, String problem) {
            this.reason = reason;
            this.path = path;
            this.problem = problem;
        }
    }

    @Test
    void mixedSequencesAgreeWithFlatModelIncludingErrorsAndHistoricalReads() {
        var random = new Random(13002342L);
        var accepted = EnumSet.noneOf(FileSystemException.Operation.class);
        var refused = EnumSet.noneOf(FileSystemException.Reason.class);
        var operations = FileSystemException.Operation.values();
        for (int scenario = 0; scenario < 80; scenario++) {
            var fs = new InMemoryFileSystem();
            var model = new Model();
            var frozen = new ArrayList<Frozen>();
            for (int step = 0; step < 120; step++) {
                var existing = new ArrayList<>(model.entries.keySet());
                String source = random.nextBoolean() ? existing.get(random.nextInt(existing.size())) : randomPath(random);
                String target = switch(random.nextInt(4)) {
                    case 0 ->
                        source;
                    case 1 ->
                        source.equals("/") ? "/x" : source + "/x";
                    default ->
                        randomPath(random);
                };
                var command = new Command(operations[random.nextInt(operations.length)], source, target,
                        "text-" + random.nextInt(20), random.nextBoolean(), random.nextInt(4));
                String context = "scenario=" + scenario + " step=" + step + " " + command;
                if (step % 20 == 0)
                    frozen.add(new Frozen(fs.snapshot(), new TreeMap<>(model.entries)));
                var before = fs.snapshot();
                Object expected = null;
                Refusal refusal = null;
                try {
                    expected = model.apply(command);
                } catch (Refusal e) {
                    refusal = e;
                }
                if (refusal == null) {
                    assertEquals(expected, actual(fs, command), context);
                    accepted.add(command.op());
                } else {
                    var actual = assertThrows(FileSystemException.class, () -> actual(fs, command), context);
                    refused.add(refusal.reason);
                    assertEquals(refusal.reason, actual.reason(), context);
                    assertEquals(command.op(), actual.operation(), context);
                    assertEquals(refusal.path, actual.path().toString(), context);
                    assertEquals(refusal.problem, actual.problemPath().toString(), context);
                    assertSame(before, fs.snapshot(), context);
                }
                assertView(model.entries, fs.snapshot(), context);
                if (step % 20 == 0 || step == 119) {
                    for (var old : frozen) assertView(old.entries(), old.view(), context + " old view");
                }
            }
        }
        assertEquals(EnumSet.allOf(FileSystemException.Operation.class), accepted);
        assertTrue(refused.containsAll(EnumSet.allOf(FileSystemException.Reason.class)), refused.toString());
    }

    private static String randomPath(Random random) {
        String result = "";
        int depth = 1 + random.nextInt(3);
        for (int i = 0; i < depth; i++) result += "/" + List.of("a", "b", "c", "f.txt", "x").get(random.nextInt(5));
        return result;
    }

    private static Object actual(InMemoryFileSystem fs, Command c) {
        return switch(c.op()) {
            case MKDIR ->
                {
                    fs.mkdir(p(c.path()));
                    yield null;
                }
            case CREATE_FILE ->
                {
                    fs.createFile(p(c.path()), c.text());
                    yield null;
                }
            case WRITE ->
                {
                    fs.write(p(c.path()), c.text());
                    yield null;
                }
            case DELETE ->
                {
                    fs.delete(p(c.path()), c.recursive() ? DeleteMode.RECURSIVE : DeleteMode.EMPTY_ONLY);
                    yield null;
                }
            case MOVE ->
                {
                    fs.move(p(c.path()), p(c.target()));
                    yield null;
                }
            case COPY ->
                {
                    fs.copy(p(c.path()), p(c.target()));
                    yield null;
                }
            case READ ->
                fs.read(p(c.path()));
            case STAT ->
                fs.stat(p(c.path())) instanceof DirectoryNode ? "dir" : "file";
            case LIST ->
                fs.list(p(c.path())).stream().map(Object::toString).toList();
            case FIND ->
                fs.find(p(c.path()), new SearchQuery(SearchCriteria.kind(SearchCriteria.Kind.FILE)
                        .and(SearchCriteria.nameContains("f")), c.depth())).stream().map(Object::toString).toList();
        };
    }

    /**
     * Sprawdzamy także list/read/stat/find widoku, nie tylko jego mapę korzenia.
     */
    private static void assertView(Map<String, String> expected, FileSystemView view, String context) {
        assertEquals(new ArrayList<>(expected.keySet()), view.find(p("/"), SearchQuery.unlimited(SearchCriteria.all()))
                .stream().map(Object::toString).toList(), context);
        for (var entry : expected.entrySet()) {
            String path = entry.getKey();
            if (entry.getValue() == null) {
                assertInstanceOf(DirectoryNode.class, view.stat(p(path)), context);
                var children = expected.keySet().stream().filter(k -> !k.equals("/") && parent(k).equals(path))
                        .toList();
                assertEquals(children, view.list(p(path)).stream().map(Object::toString).toList(), context);
            } else
                assertEquals(entry.getValue(), view.read(p(path)), context);
        }
    }

    private static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash == 0 ? "/" : path.substring(0, slash);
    }

    private static boolean within(String source, String path) {
        return source.equals("/") || source.equals(path) || path.startsWith(source + "/");
    }

    private static int depth(String path) {
        return path.equals("/") ? 0 : path.split("/").length - 1;
    }

    private static final class Model {

        final TreeMap<String, String> entries = new TreeMap<>();

        Model() {
            entries.put("/", null);
        }

        void fail(FileSystemException.Reason reason, String path, String problem) {
            throw new Refusal(reason, path, problem);
        }

        void protect(String path) {
            if (path.equals("/"))
                fail(ROOT_PROTECTED, path, path);
        }

        void resolve(String lookup, String requested) {
            String prefix = "/";
            if (lookup.equals("/"))
                return;
            for (String segment : lookup.substring(1).split("/")) {
                if (entries.get(prefix) != null)
                    fail(NOT_DIRECTORY, requested, prefix);
                prefix = prefix.equals("/") ? "/" + segment : prefix + "/" + segment;
                if (!entries.containsKey(prefix))
                    fail(NOT_FOUND, requested, prefix);
            }
        }

        void directory(String lookup, String requested) {
            resolve(lookup, requested);
            if (entries.get(lookup) != null)
                fail(NOT_DIRECTORY, requested, lookup);
        }

        void free(String path) {
            if (entries.containsKey(path))
                fail(ALREADY_EXISTS, path, path);
        }

        Object apply(Command c) {
            String path = c.path(), target = c.target();
            switch(c.op()) {
                case MKDIR, CREATE_FILE ->
                    {
                        protect(path);
                        directory(parent(path), path);
                        free(path);
                        entries.put(path, c.op() == MKDIR ? null : c.text());
                    }
                case WRITE ->
                    {
                        resolve(path, path);
                        if (entries.get(path) == null)
                            fail(NOT_FILE, path, path);
                        entries.put(path, c.text());
                    }
                case DELETE ->
                    {
                        protect(path);
                        resolve(path, path);
                        if (entries.get(path) == null && !c.recursive() && entries.keySet().stream()
                                .anyMatch(k -> !k.equals(path) && within(path, k))) {
                            fail(DIRECTORY_NOT_EMPTY, path, path);
                        }
                        entries.keySet().removeIf(k -> within(path, k));
                    }
                case MOVE, COPY ->
                    {
                        protect(path);
                        protect(target);
                        resolve(path, path);
                        if (path.equals(target) || entries.get(path) == null && within(path, target)) {
                            fail(c.op() == MOVE ? INVALID_MOVE : INVALID_COPY, path, target);
                        }
                        directory(parent(target), target);
                        free(target);
                        var transferred = new TreeMap<String, String>();
                        entries.forEach((k, v) -> {
                            if (within(path, k))
                                transferred.put(target + k.substring(path.length()), v);
                        });
                        if (c.op() == MOVE)
                            entries.keySet().removeIf(k -> within(path, k));
                        entries.putAll(transferred);
                    }
                case READ ->
                    {
                        resolve(path, path);
                        if (entries.get(path) == null)
                            fail(NOT_FILE, path, path);
                        return entries.get(path);
                    }
                case STAT ->
                    {
                        resolve(path, path);
                        return entries.get(path) == null ? "dir" : "file";
                    }
                case LIST ->
                    {
                        directory(path, path);
                        return entries.keySet().stream().filter(k -> !k.equals("/") && parent(k).equals(path)).toList();
                    }
                case FIND ->
                    {
                        resolve(path, path);
                        return entries.entrySet().stream().filter(e -> within(path, e.getKey())
                                && depth(e.getKey()) - depth(path) <= c.depth() && e.getValue() != null && e.getKey()
                                .substring(e.getKey().lastIndexOf('/') + 1).contains("f")).map(Map.Entry::getKey)
                                .toList();
                    }
            }
            return null;
        }
    }
}
