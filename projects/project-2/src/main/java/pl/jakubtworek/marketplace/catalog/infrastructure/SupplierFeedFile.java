package pl.jakubtworek.marketplace.catalog.infrastructure;

import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.math.BigDecimal;
import java.io.*;

/** Celowo wąski format UTF-8: sku,amount bez nagłówka, cudzysłowów i wieloliniowych pól. */
public final class SupplierFeedFile {
    private final SupplierCatalogImport importer;
    public SupplierFeedFile(SupplierCatalogImport importer) { this.importer = importer; }
    public void load(UUID run, Path input, boolean allowRejected) throws IOException {
        Path snapshot = Files.createTempFile("supplier-feed-", ".snapshot");
        try {
            // Hash i parser czytają tę samą prywatną kopię. Zmiana źródła w czasie kopiowania
            // wymaga kontraktu atomic rename/immutable object po stronie dostawcy.
            Files.copy(input, snapshot, StandardCopyOption.REPLACE_EXISTING);
            String digest;
            try (var stream = new DigestInputStream(Files.newInputStream(snapshot), MessageDigest.getInstance("SHA-256"))) {
                stream.transferTo(OutputStream.nullOutputStream());
                digest = HexFormat.of().formatHex(stream.getMessageDigest().digest());
            } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
            importer.begin(run, digest);
            try (var reader = Files.newBufferedReader(snapshot)) {
                var chunk = new ArrayList<SupplierCatalogImport.Input>(1000);
                long offset = 0;
                String line;
                while ((line = boundedLine(reader)) != null) {
                    try {
                        String[] fields = line.split(",", -1);
                        if (fields.length != 2) throw new IllegalArgumentException();
                        chunk.add(new SupplierCatalogImport.Input(new SupplierCatalogImport.Row(fields[0], new BigDecimal(fields[1])), null));
                    } catch (IllegalArgumentException invalid) { chunk.add(new SupplierCatalogImport.Input(null, "INVALID_ROW")); }
                    if (chunk.size() == 1000) { importer.appendInputs(run, digest, offset, chunk); offset += chunk.size(); chunk.clear(); }
                }
                if (!chunk.isEmpty()) { importer.appendInputs(run, digest, offset, chunk); offset += chunk.size(); }
                importer.complete(run, digest, offset, allowRejected);
            } catch (RuntimeException | IOException failure) { importer.fail(run); throw failure; }
        } finally { Files.deleteIfExists(snapshot); }
    }
    private String boundedLine(Reader reader) throws IOException {
        var line = new StringBuilder(512);
        int value;
        boolean seen = false, overflow = false;
        while ((value = reader.read()) != -1 && value != '\n') {
            seen = true;
            if (line.length() < 512) line.append((char) value); else overflow = true;
        }
        if (!seen && value == -1) return null;
        if (overflow) return ""; // Kwarantanna; nawet wielomegabajtowa linia nie trafia w całości do heapu.
        if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') line.setLength(line.length() - 1);
        return line.toString();
    }
}
