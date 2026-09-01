package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests;

/**
 * Minimal abstraction over traffic generation.
 *
 * Real implementations can call k6, Gatling, JMeter, Locust,
 * or an internal load generation platform.
 */
public interface TrafficGenerator {

    void runAtRps(int rps, long seconds) throws Exception;
}