package pl.jakubtworek.lld.parkinglot;

import java.util.List;
import java.util.Optional;

/**
 * Wymienny wybór spośród kandydatów: wszystkich JUŻ wolnych i zgodnych miejsc.
 * Rozdzielamy regułę dopuszczalności od preferencji. Strategia nie może
 * „pozwolić” vanowi na motocyklowe miejsce, nie tworzy biletu i nie zna zegara.
 *
 * Kontrakt: czysta, niereentrantna operacja, lista niezmienna, wynik należy
 * do kandydatów; Optional.empty tylko przy pustej liście. Naruszenie kontraktu
 * jest błędem implementacji strategii, nie odmową braku miejsca. ParkingLot
 * sprawdza wynik przed publikacją stanu. Nie potrafi cofnąć cudzych efektów I/O.
 */
@FunctionalInterface
public interface SpotAllocationStrategy {

    Optional<ParkingSpot> select(List<ParkingSpot> candidates);

    /**
     * Metoda default nie dodaje metody abstrakcyjnej: interfejs nadal przyjmuje
     * lambdę select(List). Ten adapter materializuje listę O(S log S);
     * implementacje wbudowane nadpisują przeciążenie, korzystając z indeksu.
     */
    default Optional<ParkingSpot> select(FreeSpotIndex index, VehicleType type) {
        return select(index.candidates(type));
    }
}
