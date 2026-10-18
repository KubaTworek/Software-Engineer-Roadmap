package com.example.observability.server.cold;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/** Weryfikuje cold object i ładuje go do izolowanej, krótkotrwałej tabeli drill. */
@Service
public class ColdRestoreService {
    private final ObjectStorageService storage;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;

    public ColdRestoreService(ObjectStorageService storage, ObjectMapper json, JdbcTemplate jdbc) {
        this.storage = storage; this.json = json; this.jdbc = jdbc;
    }

    public RestoreResult drill(String tenantId, String objectKey) {
        if (!objectKey.startsWith("logs/" + tenantId + "/")
                && !objectKey.startsWith("metrics/" + tenantId + "/"))
            throw new IllegalArgumentException("object does not belong to authenticated tenant");
        try {
            byte[] compressed = storage.read(objectKey);
            var manifest = json.readTree(storage.read(objectKey + ".manifest.json"));
            String actualHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(compressed));
            if (!actualHash.equals(manifest.path("sha256").asText()))
                throw new IllegalStateException("cold object checksum mismatch");
            UUID drillId = UUID.randomUUID();
            long rows = 0;
            try (var gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
                 var reader = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    json.readTree(line);
                    jdbc.update("""
                            INSERT INTO restore_drill_rows
                              (drill_id, tenant_id, object_key, row_number, payload_json)
                            VALUES (?,?,?,?,?)
                            """, drillId.toString(), tenantId, objectKey, ++rows, line);
                }
            }
            long expected = manifest.path("rows").asLong(-1);
            if (rows != expected) throw new IllegalStateException("cold object row count mismatch");
            return new RestoreResult(drillId.toString(), tenantId, objectKey, rows, actualHash, "verified");
        } catch (RuntimeException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("restore drill failed", exception); }
    }

    public record RestoreResult(String drillId, String tenantId, String objectKey,
                                long rows, String sha256, String status) { }
}
