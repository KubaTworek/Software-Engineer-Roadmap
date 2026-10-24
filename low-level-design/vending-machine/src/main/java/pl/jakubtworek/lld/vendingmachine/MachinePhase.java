package pl.jakubtworek.lld.vendingmachine;

/**
 * Faza odczytowa, wyprowadzana z sesji i ceny, a nie osobno mutowana flaga.
 * FUNDED oznacza wystarczający kredyt, nie gwarancję dostępnej reszty.
 * Nie pokazujemy stanu DISPENSING: model nie wykonuje asynchronicznego I/O.
 */
public enum MachinePhase {

    IDLE, COLLECTING, FUNDED
}
