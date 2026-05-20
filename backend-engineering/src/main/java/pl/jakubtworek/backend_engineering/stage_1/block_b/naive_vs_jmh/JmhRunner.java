package pl.jakubtworek.backend_engineering.stage_1.block_b.naive_vs_jmh;

import org.openjdk.jmh.Main;

public final class JmhRunner {

    public static void main(String[] args) throws Exception {
        // This optional runner delegates to JMH.
        // In most Maven setups, the shaded benchmark jar can run JMH directly.
        Main.main(args);
    }
}