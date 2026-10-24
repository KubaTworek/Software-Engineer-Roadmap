package pl.jakubtworek.lld.tictactoe;

/**
 * Współrzędne liczymy od zera. Jest to value object: dwie pozycje o tych samych
 * współrzędnych oznaczają to samo pole, niezależnie od tożsamości obiektu.
 * Pozycja nie zna rozmiaru planszy. Jej przynależność do konkretnej planszy
 * sprawdza Board; dzięki temu nie duplikujemy walidacji zależnej od rozmiaru.
 * Cel edukacyjny: równość wartości nie oznacza poprawności w każdym kontekście.
 * Position(4,4) jest wewnątrz planszy 5x5, ale poza planszą 3x3. Nawet ujemne
 * współrzędne można opisać tym typem, aby granica Board zwróciła OUT_OF_BOUNDS.
 * Record zapewnia tu równość składowych bez potrzeby identyfikatora lub setterów.
 */
public record Position(int row, int column) {
}
