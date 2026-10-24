package pl.jakubtworek.backend_engineering.stage_1.block_g;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Uruchamiaj tylko w kontenerze z limitem pamięci/CPU/pids i zewnętrznym timeoutem. */
public final class ResourcePressureProbe {
    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "cpu" : args[0];
        System.out.println("heapMax=" + Runtime.getRuntime().maxMemory() + " pid=" + ProcessHandle.current().pid());
        switch (mode) {
            case "memory" -> {
                var buffers = new ArrayList<ByteBuffer>();
                for (int i = 0; i < 256; i++) {
                    var buffer = ByteBuffer.allocateDirect(4 * 1024 * 1024);
                    // Dotykamy stron: sama rezerwacja przestrzeni adresowej nie jest pomiarem RSS.
                    for (int j = 0; j < buffer.capacity(); j += 4096) buffer.put(j, (byte) 1);
                    buffers.add(buffer);
                    Thread.sleep(10);
                }
                System.out.println("directBuffers=" + buffers.size());
            }
            case "fd" -> {
                var streams = new ArrayList<java.io.InputStream>();
                try {
                    for (int i = 0; i < 2048; i++) streams.add(Files.newInputStream(Path.of("/dev/null")));
                } finally {
                    for (var stream : streams) stream.close();
                }
            }
            case "cpu" -> {
                long deadline = System.nanoTime() + java.time.Duration.ofSeconds(8).toNanos();
                long result = 1;
                while (System.nanoTime() < deadline) result = Long.rotateLeft(result * 31 + 17, 7);
                System.out.println("checksum=" + result);
                Path v2 = Path.of("/sys/fs/cgroup/cpu.stat");
                Path statistics = Files.exists(v2) ? v2 : Path.of("/sys/fs/cgroup/cpu/cpu.stat");
                System.out.println("cpuStatistics=" + statistics);
                System.out.println(Files.readString(statistics));
            }
            default -> throw new IllegalArgumentException("memory | cpu | fd");
        }
    }
}
