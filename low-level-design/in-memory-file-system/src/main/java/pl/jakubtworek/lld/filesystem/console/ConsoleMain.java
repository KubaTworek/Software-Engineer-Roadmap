package pl.jakubtworek.lld.filesystem.console;

import pl.jakubtworek.lld.filesystem.domain.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Adapter wykonuje dopiero poprawnie sparsowaną komendę. save zachowuje widok,
 * use wybiera odczyt historyczny, live wraca do aktualnego stanu. Mutacje
 * historycznego widoku są odrzucane w adapterze; sam FileSystemView nie ma
 * metod zapisu. Strumienie należą do wywołującego, nie do konsoli.
 */
public final class ConsoleMain {

    private ConsoleMain() {
    }

    public static void main(String[] args) {
        run(new InputStreamReader(System.in, StandardCharsets.UTF_8), new PrintWriter(System.out, true,
                StandardCharsets.UTF_8));
    }

    public static void run(Readable source, PrintWriter out) {
        var input = new Scanner(source);
        var fs = new InMemoryFileSystem();
        var saved = new HashMap<String, FileSystemView>();
        FileSystemView selected = null;
        out.println("System plików w pamięci — projekt edukacyjny. help, q. Dane znikają po zakończeniu.");
        while (input.hasNextLine()) {
            String line = input.nextLine();
            if (line.isBlank())
                continue;
            try {
                var command = CommandParser.parse(line);
                var a = command.arguments();
                String name = command.name();
                if (name.equals("q"))
                    break;
                if (selected != null && Set.of("mkdir", "create", "write", "move", "copy", "rm", "rm-tree")
                        .contains(name)) {
                    throw new IllegalArgumentException("Widok historyczny jest tylko do odczytu; live");
                }
                FileSystemView read = selected == null ? fs.snapshot() : selected;
                switch(name) {
                    case "help" ->
                        out.println("mkdir PATH; create PATH | text; write PATH | text; read PATH; ls PATH; find START | fragment; search START ANY|FILE|DIRECTORY EXT|- DEPTH FRAGMENT|-; move SOURCE TARGET; copy SOURCE TARGET; rm PATH; rm-tree PATH; save NAME; use NAME; live; q. Ścieżki ze spacjami umieść w cudzysłowach.");
                    case "mkdir" ->
                        {
                            fs.mkdir(FsPath.parse(a.getFirst()));
                            out.println("OK");
                        }
                    case "create" ->
                        {
                            fs.createFile(FsPath.parse(a.getFirst()), a.get(1));
                            out.println("OK");
                        }
                    case "write" ->
                        {
                            fs.write(FsPath.parse(a.getFirst()), a.get(1));
                            out.println("OK");
                        }
                    case "read" ->
                        out.println(read.read(FsPath.parse(a.getFirst())));
                    case "ls" ->
                        read.list(FsPath.parse(a.getFirst())).forEach(out::println);
                    case "find" ->
                        read.find(FsPath.parse(a.getFirst()), a.get(1)).forEach(out::println);
                    case "search" ->
                        {
                            SearchCriteria criteria = a.get(1)
                                    .equals("ANY") ? SearchCriteria.all() : SearchCriteria.kind(SearchCriteria.Kind.valueOf(a.get(1)));
                            if (!a.get(2).equals("-"))
                                criteria = criteria.and(SearchCriteria.extension(a.get(2)));
                            if (!a.get(4).equals("-"))
                                criteria = criteria.and(SearchCriteria.nameContains(a.get(4)));
                            read.find(FsPath.parse(a.getFirst()), new SearchQuery(criteria, Integer.parseInt(a.get(3))))
                                    .forEach(out::println);
                        }
                    case "move" ->
                        {
                            fs.move(FsPath.parse(a.getFirst()), FsPath.parse(a.get(1)));
                            out.println("OK");
                        }
                    case "copy" ->
                        {
                            fs.copy(FsPath.parse(a.getFirst()), FsPath.parse(a.get(1)));
                            out.println("OK");
                        }
                    case "rm", "rm-tree" ->
                        {
                            fs.delete(FsPath.parse(a.getFirst()),
                                    name.equals("rm-tree") ? DeleteMode.RECURSIVE : DeleteMode.EMPTY_ONLY);
                            out.println("OK");
                        }
                    case "save" ->
                        {
                            saved.put(a.getFirst(), read);
                            out.println("Zapisano widok: " + a.getFirst());
                        }
                    case "use" ->
                        {
                            var candidate = saved.get(a.getFirst());
                            if (candidate == null)
                                throw new IllegalArgumentException("Nieznany widok");
                            selected = candidate;
                            out.println("Widok: " + a.getFirst());
                        }
                    case "live" ->
                        {
                            selected = null;
                            out.println("Widok: live");
                        }
                    default ->
                        throw new IllegalArgumentException("Nieznana komenda; help");
                }
            } catch (IllegalArgumentException ex) {
                out.println("Odmowa: " + ex.getMessage());
            }
        }
        out.flush();
    }
}
