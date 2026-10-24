package pl.jakubtworek.lld.workflow.console;

import pl.jakubtworek.lld.workflow.domain.*;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

/**
 * Lokalny adapter dla jednego dokumentu. Wybór aliasu aktora jest symulacją,
 * nie logowaniem. Domena decyduje o uprawnieniach i przejściach; konsola tylko
 * parsuje polecenia. Nowy dokument zastępuje stary dopiero po poprawnej konstrukcji.
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
        var actors = Map.of("alice", new Actor(new UserId("alice"), Set.of(Role.AUTHOR, Role.REVIEWER, Role.MANAGER)),
                "bob", new Actor(new UserId("bob"), Set.of(Role.REVIEWER)), "carol", new Actor(new UserId("carol"),
                Set.of(Role.MANAGER)), "dave", new Actor(new UserId("dave"), Set.of(Role.REVIEWER, Role.MANAGER)),
                "guest", new Actor(new UserId("guest"), Set.of()));
        var definition = new WorkflowDefinition(List.of(new ReviewStage("technical", Role.REVIEWER),
                new ReviewStage("manager", Role.MANAGER)));
        Clock clock = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC);
        Actor actor = actors.get("alice");
        // Zero oznacza pobranie wersji bieżącego widoku. expect przypina wersję
        // aż do latest/new: dwie kolejne intencje pozwalają odtworzyć konflikt.
        long expectedVersion = 0;
        ReviewerPolicies policy = ReviewerPolicies.ALLOW_REUSE;
        var document = new Document(new DocumentId("DOC1"), actor, new DocumentContent("Projekt", "Pierwsza wersja"),
                definition, clock, policy);
        out.println("Workflow dokumentów — projekt edukacyjny. Autor alice, recenzent bob, manager carol, dave ma obie role. help, q.");
        render(document, actor, out);
        while (input.hasNextLine()) {
            String line = input.nextLine().strip();
            if (line.equals("q"))
                break;
            if (line.isEmpty())
                continue;
            try {
                String[] parts = line.split("\\s+");
                if (line.equals("help")) {
                    out.println("as alice|bob|carol|dave|guest; show; plan; history; submit; approve [round revision stage]; reject reason;");
                    out.println("edit title | body; new ID title | body; withdraw reason; actions; archive; expect VERSION; latest; policy ALLOW_REUSE|DISTINCT_PER_ROUND; q. Wszystkie dane są w pamięci.");
                } else if (parts.length == 2 && parts[0].equals("as")) {
                    Actor candidate = actors.get(parts[1]);
                    if (candidate == null)
                        throw new IllegalArgumentException("Nieznany aktor");
                    actor = candidate;
                    out.println("Aktor: " + actor.id());
                } else if (line.equals("show"))
                    render(document, actor, out);
                else if (line.equals("plan"))
                    document.definition().stages().forEach(s -> out.println(s.id() + ": " + s.requiredRole()));
                else if (line.equals("history"))
                    document.snapshot().history()
                            .forEach(e -> out.println(e.sequence() + " " + e.at() + " " + e.actor() + " " + e.type() + " rev=" + e.revision() + " round=" + e.round() + " stage=" + e.stageId().orElse("-") + " note=" + e.note().orElse("-") + " -> " + e.resultingStatus()));
                else if (parts.length == 2 && parts[0].equals("expect")) {
                    long candidate = Long.parseLong(parts[1]);
                    if (candidate < 1)
                        throw new IllegalArgumentException("Wersja musi być dodatnia");
                    expectedVersion = candidate;
                    out.println("Oczekiwana wersja: " + expectedVersion);
                } else if (line.equals("latest"))
                    expectedVersion = 0;
                else if (parts.length == 2 && parts[0].equals("policy")) {
                    policy = ReviewerPolicies.valueOf(parts[1]);
                    out.println("Polityka kolejnego dokumentu: " + policy);
                } else if (line.equals("actions"))
                    document.availableOperations(actor).operations().forEach((op,
                            assessment) -> out.println(op + ": " + assessment));
                else if (line.equals("archive"))
                    document.snapshot().revisions().forEach(out::println);
                else if (line.equals("withdraw") || line.startsWith("withdraw ")) {
                    document.withdraw(actor, expectedVersion == 0 ? document.snapshot().version() : expectedVersion,
                            line.length() == 8 ? "" : line.substring(9));
                    render(document, actor, out);
                } else if (line.equals("submit")) {
                    document.submit(actor, expectedVersion == 0 ? document.snapshot().version() : expectedVersion);
                    render(document, actor, out);
                } else if (parts[0].equals("approve") && (parts.length == 1 || parts.length == 4)) {
                    ReviewToken token = parts.length == 1 ? currentToken(document) : new ReviewToken(document.snapshot()
                            .id(), Integer.parseInt(parts[1]), Long.parseLong(parts[2]), parts[3]);
                    document.approve(actor, expectedVersion == 0 ? document.snapshot().version() : expectedVersion,
                            token);
                    render(document, actor, out);
                } else if (line.equals("reject") || line.startsWith("reject ")) {
                    document.reject(actor, expectedVersion == 0 ? document.snapshot().version() : expectedVersion,
                            currentToken(document), line.length() == 6 ? "" : line.substring(7));
                    render(document, actor, out);
                } else if (line.startsWith("edit ")) {
                    document.edit(actor, expectedVersion == 0 ? document.snapshot().version() : expectedVersion,
                            content(line.substring(5)));
                    render(document, actor, out);
                } else if (line.startsWith("new ")) {
                    String[] data = line.substring(4).split("\\s+", 2);
                    if (data.length != 2)
                        throw new IllegalArgumentException("new ID title | body");
                    Document candidate = new Document(new DocumentId(data[0]), actor, content(data[1]), definition,
                            clock, policy);
                    document = candidate;
                    expectedVersion = 0;
                    render(document, actor, out);
                } else
                    throw new IllegalArgumentException("Nieznana komenda lub liczba argumentów; help");
            } catch (IllegalArgumentException | ArithmeticException ex) {
                out.println("Odmowa: " + ex.getMessage());
            }
        }
        // Dostarczonych strumieni nie zamykamy; EOF jest poprawnym końcem sesji.
        out.flush();
    }

    private static DocumentContent content(String text) {
        String[] fields = text.split("\\|", 2);
        if (fields.length != 2)
            throw new IllegalArgumentException("Treść: title | body");
        return new DocumentContent(fields[0], fields[1]);
    }

    private static ReviewToken currentToken(Document document) {
        return document.reviewToken().orElseThrow(() -> new WorkflowException(WorkflowException.Reason.WRONG_STATE));
    }

    private static void render(Document document, Actor actor, PrintWriter out) {
        DocumentSnapshot view = document.snapshot();
        out.println("Dokument " + view.id() + "; stan: " + view.status() + "; rewizja: " + view.revision() + "; runda: " + view.review().map(ReviewRound::number).orElse(0) + "; aktor: " + actor.id() + "; wersja: " + view.version() + "; ostatnia runda: " + view.lastRound());
        out.println(view.content().title() + " | " + view.content().body());
        document.reviewToken()
                .ifPresent(t -> out.println("Token: " + t.round() + " " + t.revision() + " " + t.stageId()));
        document.currentStage().ifPresent(s -> out.println("Wymagana rola: " + s.requiredRole()));
    }
}
