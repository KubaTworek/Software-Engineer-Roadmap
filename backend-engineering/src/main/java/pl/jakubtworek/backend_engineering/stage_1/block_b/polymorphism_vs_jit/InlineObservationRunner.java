package pl.jakubtworek.backend_engineering.stage_1.block_b.polymorphism_vs_jit;

public class InlineObservationRunner {

    public static void main(String[] args) {
        // Run with:
        //
        // -XX:+UnlockDiagnosticVMOptions
        // -XX:+PrintInlining
        // -XX:+PrintCompilation
        //
        // Example:
        //
        // java -XX:+UnlockDiagnosticVMOptions ^
        //      -XX:+PrintInlining ^
        //      -XX:+PrintCompilation ^
        //      MonomorphicCallSiteDemo
        //
        // The goal is to observe:
        // - successful inlining,
        // - failed inlining,
        // - virtual call optimization behavior,
        // - differences between mono/bi/megamorphic call-sites.

        System.out.println("Run individual demos with JIT diagnostic flags.");
    }
}