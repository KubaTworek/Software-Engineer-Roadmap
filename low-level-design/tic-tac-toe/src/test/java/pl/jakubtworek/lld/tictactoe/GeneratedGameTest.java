package pl.jakubtworek.lld.tictactoe;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy generowane są powtarzalne: to samo ziarno daje te same partie.
 * Nie zastępują dowodu dla dowolnego N, ale sprawdzają znacznie więcej układów
 * niż ręcznie wybrane przykłady. Nie wymagają biblioteki property-based testing.
 * Cel edukacyjny: sprawdzać ogólne własności (wynik, turę, stabilność widoków)
 * na wielu danych, a następnie użyć symetrii jako relacji pomiędzy wynikami.
 * Prawidłowe działanie na próbce nie wyklucza błędu na innej historii.
 */
class GeneratedGameTest {

    static Stream<Arguments> configurations() {
        // Każda z 36 par (N,K) jest osobnym przypadkiem raportowanym przez JUnit.
        // 32 partie wewnątrz przypadku nie są 32 oddzielnymi testami raportu.
        return IntStream.rangeClosed(1, 8).boxed().flatMap(size -> IntStream.rangeClosed(1, size)
                .mapToObj(length -> Arguments.of(size, length)));
    }

    @ParameterizedTest(name = "N={0}, K={1}: 32 deterministyczne partie")
    @MethodSource("configurations")
    void generatedGamesAgreeWithFullBoardOracleAndPreserveArchivedViews(int size, int length) {
        var random = new Random(0x5EEDL + 31L * size + length);
        for (int sample = 0; sample < 32; sample++) {
            var game = new TicTacToeGame(new GameRules(size, length));
            // Niezależna reprezentacja: 0=pusto, 1=X, 2=O. Oczekiwania
            // obliczamy z tej macierzy, nie z odczytu badanej planszy.
            int[][] reference = new int[size][size];
            var archived = new ArrayList<Archived>();
            archived.add(new Archived(game.snapshot(), copy(reference)));
            int moveCount = 0;
            for (Position position : shuffledPositions(size, random)) {
                Mark mark = moveCount % 2 == 0 ? Mark.X : Mark.O;
                reference[position.row()][position.column()] = mark == Mark.X ? 1 : 2;
                moveCount++;
                var actual = game.play(mark, position);
                GameStatus expected = fullScan(reference, length, moveCount);
                assertEquals(expected, actual.status(),
                        "N=" + size + ", K=" + length + ", próba=" + sample + ", ruch=" + moveCount);
                assertEquals(expected == GameStatus.IN_PROGRESS ? Optional.of(mark.opponent()) : Optional.empty(),
                        actual.nextPlayer());
                assertBoardEquals(reference, actual.board());
                archived.add(new Archived(actual, copy(reference)));
                if (expected != GameStatus.IN_PROGRESS) {
                    var beforeRejection = game.snapshot();
                    var error = assertThrows(IllegalMoveException.class, () -> game.play(mark.opponent(), position));
                    assertEquals(IllegalMoveException.Reason.GAME_FINISHED, error.reason());
                    assertSame(beforeRejection, game.snapshot());
                    break;
                }
            }
            // Sprawdzamy WSZYSTKIE pola wszystkich dawnych widoków dopiero po
            // zakończeniu partii. To wykrywa przypadkową mutację współdzielonego
            // wiersza, także wtedy, gdy kolejne ruchy trafiają do tego samego rzędu.
            for (Archived old : archived) {
                assertBoardEquals(old.expected(), old.snapshot().board());
            }
        }
    }

    @ParameterizedTest
    @CsvSource({ "3,3", "5,4", "6,3", "8,5" })
    void rotationsAndReflectionsPreserveEveryIntermediateResult(int size, int length) {
        // To test metamorficzny: po obrocie lub odbiciu tej samej sekwencji
        // zmieniają się współrzędne, ale nie wynik i kolejność uczestników.
        // Taka relacja może wykryć brak jednej przekątnej nawet bez ręcznego
        // wyliczania zwycięzcy. Do niezależnego ustalenia wyniku służy drugi test.
        var random = new Random(0xD4L + size * 31L + length);
        for (int sample = 0; sample < 12; sample++) {
            var games = IntStream.range(0, 8).mapToObj(ignored -> new TicTacToeGame(new GameRules(size, length)))
                    .toList();
            int depth = 0;
            for (Position position : shuffledPositions(size, random)) {
                Mark mark = depth++ % 2 == 0 ? Mark.X : Mark.O;
                for (int symmetry = 0; symmetry < 8; symmetry++) {
                    games.get(symmetry).play(mark, transform(position, size, symmetry));
                }
                var original = games.getFirst().snapshot();
                for (int symmetry = 1; symmetry < 8; symmetry++) {
                    var transformed = games.get(symmetry).snapshot();
                    assertEquals(original.status(), transformed.status());
                    assertEquals(original.nextPlayer(), transformed.nextPlayer());
                    for (int row = 0; row < size; row++) {
                        for (int column = 0; column < size; column++) {
                            var source = new Position(row, column);
                            assertEquals(original.board().markAt(source), transformed.board().markAt(transform(source,
                                    size, symmetry)));
                        }
                    }
                }
                if (original.status() != GameStatus.IN_PROGRESS) {
                    break;
                }
            }
        }
    }

    private static GameStatus fullScan(int[][] cells, int length, int moves) {
        int size = cells.length;
        // Wyrocznia bada każde okno K pól, dla każdego pola początkowego.
        // Nie zna ostatniego ruchu i nie liczy dwóch połówek osi. Dzięki temu
        // nie powiela optymalizacji, której poprawność ma sprawdzać.
        int[][] directions = { { 0, 1 }, { 1, 0 }, { 1, 1 }, { 1, -1 } };
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (cells[row][column] == 0) {
                    continue;
                }
                for (int[] direction : directions) {
                    int matched = 0;
                    for (int offset = 0; offset < length; offset++) {
                        int r = row + offset * direction[0];
                        int c = column + offset * direction[1];
                        if (r < 0 || r >= size || c < 0 || c >= size || cells[r][c] != cells[row][column]) {
                            break;
                        }
                        matched++;
                    }
                    if (matched == length) {
                        return cells[row][column] == 1 ? GameStatus.X_WON : GameStatus.O_WON;
                    }
                }
            }
        }
        return moves == size * size ? GameStatus.DRAW : GameStatus.IN_PROGRESS;
    }

    private static Position transform(Position p, int size, int symmetry) {
        // Cztery obroty, a następnie cztery obroty odbicia: osiem symetrii
        // kwadratu. Znaki graczy i kolejność ruchów pozostają takie same.
        int row = p.row();
        int column = symmetry >= 4 ? size - 1 - p.column() : p.column();
        for (int rotation = 0; rotation < symmetry % 4; rotation++) {
            int oldRow = row;
            row = column;
            column = size - 1 - oldRow;
        }
        return new Position(row, column);
    }

    private static List<Position> shuffledPositions(int size, Random random) {
        // Tasujemy wszystkie pola bez powtórzeń, więc generujemy legalne
        // propozycje do momentu końca gry. Odmowy na zajętym polu są osobnym
        // scenariuszem; ten generator nie ma ich losowo pokrywać.
        var positions = new ArrayList<Position>();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                positions.add(new Position(row, column));
            }
        }
        Collections.shuffle(positions, random);
        return positions;
    }

    private static int[][] copy(int[][] source) {
        // Oczekiwania celowo kopiujemy głęboko, inaczej kolejne ruchy zmieniłyby
        // także „historię” wyroczni i test mógłby fałszywie potwierdzić błąd.
        // Nie kopiujemy optymalizacji współdzielenia z kodu badanego modelu.
        return Stream.of(source).map(int[]::clone).toArray(int[][]::new);
    }

    private static void assertBoardEquals(int[][] expected, Board actual) {
        for (int row = 0; row < expected.length; row++) {
            for (int column = 0; column < expected.length; column++) {
                Optional<Mark> mark = switch(expected[row][column]) {
                    case 1 ->
                        Optional.of(Mark.X);
                    case 2 ->
                        Optional.of(Mark.O);
                    default ->
                        Optional.empty();
                };
                assertEquals(mark, actual.markAt(new Position(row, column)));
            }
        }
    }

    private record Archived(GameSnapshot snapshot, int[][] expected) {
    }
}
