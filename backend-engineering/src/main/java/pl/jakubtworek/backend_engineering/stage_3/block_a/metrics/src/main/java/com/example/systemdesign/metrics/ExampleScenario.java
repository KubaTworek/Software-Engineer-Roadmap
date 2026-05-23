package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

/**
 * Demonstrates the numeric example from the system design notes.
 */
public class ExampleScenario {

    public static void main(String[] args) {
        RequestPathProfile profile = new RequestPathProfile(
                4,      // replicas
                2,      // vCPU per replica
                0.70,   // target CPU utilization
                0.003,  // CPU seconds per request
                30,     // payment dependency pool size
                0.4,    // payment dependency latency in seconds
                0.10,   // fraction of requests touching payment
                1200,   // safe database write QPS limit
                0.20,   // write ratio
                2,      // write queries per request
                0.20,   // cache miss ratio
                1       // read queries on cache miss
        );

        BottleneckPrediction first = BottleneckAnalyzer.predictFirst(profile);

        System.out.println("First bottleneck: " + first.type());
        System.out.println("Approximate limit RPS: " + first.limitRps());
        System.out.println("Why: " + first.explanation());
        System.out.println("Confirm with: " + first.confirmingMetric());

        double paymentConcurrencyAt750Rps = CapacityFormula.concurrency(
                750 * 0.10,
                0.4
        );

        System.out.println("Payment concurrency at 750 RPS: " + paymentConcurrencyAt750Rps);
    }
}
