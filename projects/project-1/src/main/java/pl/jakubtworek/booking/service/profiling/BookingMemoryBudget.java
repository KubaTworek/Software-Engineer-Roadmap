package pl.jakubtworek.booking.service.profiling;

/** Budżet jednej repliki Booking; jednostką są bajty, a nie procent heapu. */
public record BookingMemoryBudget(long containerLimit, long heapMax, long nativeReserve) {
    public BookingMemoryBudget {
        if (containerLimit <= 0 || heapMax <= 0 || nativeReserve < 0)
            throw new IllegalArgumentException("Niepoprawny budżet pamięci");
    }
    public boolean fits() {
        // Odejmowanie zapobiega overflow sumy. Rezerwa obejmuje stosy, direct buffers i VM.
        return heapMax <= containerLimit && nativeReserve <= containerLimit - heapMax;
    }
    public long remainingForNative() { return Math.max(0, containerLimit - heapMax); }
}
