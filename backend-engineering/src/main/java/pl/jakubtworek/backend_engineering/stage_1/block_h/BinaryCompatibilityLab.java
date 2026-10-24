package pl.jakubtworek.backend_engineering.stage_1.block_h;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Prawdziwy linker JVM, bez symulowania NoSuchMethodError ręcznie rzuconym wyjątkiem. */
public final class BinaryCompatibilityLab {
    public record Result(int exitCode, String output) {}

    public static Result run(Path workspace, boolean compatible) throws Exception {
        Path api = Files.createDirectories(workspace.resolve("api"));
        Path client = Files.createDirectories(workspace.resolve("client"));
        compile(api, "Library", "public class Library { public static String value() { return \"v1\"; } }", null);
        compile(client, "Client", "public class Client { public static void main(String[] a) { System.out.println(Library.value()); } }", api);
        // Klient pozostaje skompilowany przeciw V1. Zmieniamy wyłącznie bibliotekę runtime.
        compile(api, "Library", compatible
                ? "public class Library { public static String value() { return \"v2\"; } }"
                : "public class Library { public static String renamed() { return \"v2\"; } }", null);
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path output = workspace.resolve("runtime.txt");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-cp", api + java.io.File.pathSeparator + client, "Client")
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!process.waitFor(Duration.ofSeconds(15).toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IOException("Proces laboratoryjny przekroczył deadline");
        }
        return new Result(process.exitValue(), Files.readString(output));
    }

    private static void compile(Path target, String name, String source, Path classpath) throws IOException {
        Path file = target.resolve(name + ".java");
        Files.writeString(file, source);
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("Uruchom na JDK, nie JRE");
        String cp = classpath == null ? target.toString() : classpath.toString();
        if (compiler.run(null, null, null, "-classpath", cp, "-d", target.toString(), file.toString()) != 0) {
            throw new IllegalStateException("Kompilacja przykładu nie powiodła się");
        }
    }
}
