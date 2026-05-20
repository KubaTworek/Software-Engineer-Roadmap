package pl.jakubtworek.backend_engineering.stage_1.block_b.lock_contention;

public final class ContentionNoise {

    private ContentionNoise() {
    }

    public static void consumeCpuBriefly() {
        // Optional helper for experiments where each update should include
        // a small amount of CPU work.
        //
        // By default, the main case study does not use this,
        // because it wants to isolate counter contention.
        long x = 0;

        for (int i = 0; i < 100; i++) {
            x += i * 31L;
        }

        if (x == System.nanoTime()) {
            System.out.println(x);
        }
    }
}