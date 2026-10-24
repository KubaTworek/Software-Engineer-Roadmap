package pl.jakubtworek.marketplace.catalog.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;

/** Catalog publikuje wyłącznie kompletny snapshot; checkpoint i efekt porcji mają jeden COMMIT. */
public final class SupplierCatalogImport {
    public record Row(String sku, BigDecimal amount) {
        public Row {
            if (sku == null || !sku.matches("[A-Za-z0-9_-]{1,120}") || amount == null || amount.signum() < 0
                    || amount.scale() > 2 || amount.precision() - amount.scale() > 17)
                throw new IllegalArgumentException("Niepoprawny wiersz feedu");
        }
    }
    public record Input(Row row, String rejection) {
        public Input {
            if ((row == null) == (rejection == null)) throw new IllegalArgumentException("Wiersz albo powód odrzucenia");
            if (rejection != null && !rejection.matches("[A-Z_]{1,80}")) throw new IllegalArgumentException("Kod powodu bez surowych danych");
        }
    }
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    public SupplierCatalogImport(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc; this.transaction = transaction;
    }
    public void begin(UUID run, String fingerprint) {
        validateFingerprint(fingerprint);
        jdbc.update("insert into catalog.supplier_import_runs(id,source_sha256) values (?,?) on conflict(id) do nothing", run, fingerprint);
    }
    public long append(UUID run, String fingerprint, long firstLine, List<Row> rows) {
        return appendInputs(run, fingerprint, firstLine, rows.stream().map(row -> new Input(row, null)).toList());
    }
    public long appendInputs(UUID run, String fingerprint, long firstLine, List<Input> rows) {
        validateFingerprint(fingerprint);
        if (firstLine < 0 || rows.isEmpty() || rows.size() > 1000) throw new IllegalArgumentException("Offset i porcja 1..1000");
        var chunk = List.copyOf(rows);
        return transaction.execute(status -> {
            jdbc.update("insert into catalog.supplier_import_runs(id,source_sha256) values (?,?) on conflict(id) do nothing", run, fingerprint);
            var state = lock(run);
            if (!fingerprint.equals(state.get("source_sha256"))
                    || !("RUNNING".equals(state.get("status")) || "COMPLETE".equals(state.get("status"))))
                throw new IllegalStateException("Inne źródło lub import nie jest RUNNING/COMPLETE");
            long next = ((Number) state.get("next_line")).longValue();
            if (firstLine < next && Math.addExact(firstLine, chunk.size()) <= next) {
                // Retry musi odpowiadać utrwalonej zawartości, a nie jedynie temu samemu offsetowi.
                for (int i = 0; i < chunk.size(); i++) verifyRetry(run, firstLine + i, chunk.get(i));
                return next;
            }
            if (firstLine != next) throw new IllegalStateException("Luka albo nakładająca się porcja");
            if (!"RUNNING".equals(state.get("status"))) throw new IllegalStateException("COMPLETE jest terminalny");
            for (int i = 0; i < chunk.size(); i++) {
                Input input = chunk.get(i);
                if (input.row() != null)
                    jdbc.update("insert into catalog.supplier_import_items(run_id,line_number,sku,amount) values (?,?,?,?)",
                            run, next + i, input.row().sku(), input.row().amount());
                else jdbc.update("insert into catalog.supplier_import_rejections(run_id,line_number,reason) values (?,?,?)",
                        run, next + i, input.rejection());
            }
            long checkpoint = Math.addExact(next, chunk.size());
            jdbc.update("update catalog.supplier_import_runs set next_line=? where id=?", checkpoint, run);
            return checkpoint;
        });
    }
    private void verifyRetry(UUID run, long line, Input input) {
        if (input.row() != null) {
            var row = jdbc.queryForMap("select sku,amount from catalog.supplier_import_items where run_id=? and line_number=?", run, line);
            if (!input.row().sku().equals(row.get("sku")) || input.row().amount().compareTo((BigDecimal) row.get("amount")) != 0)
                throw new IllegalStateException("Zmieniona treść zatwierdzonej porcji");
        } else if (!input.rejection().equals(jdbc.queryForObject(
                "select reason from catalog.supplier_import_rejections where run_id=? and line_number=?", String.class, run, line)))
            throw new IllegalStateException("Zmieniony powód odrzucenia");
    }
    /** EOF i liczba linii pochodzą z adaptera immutable input, nie z niezaufanego requestu klienta. */
    public void complete(UUID run, String fingerprint, long eofLines, boolean allowRejected) {
        transaction.executeWithoutResult(tx -> {
            var state = lock(run);
            if ("COMPLETE".equals(state.get("status")) && fingerprint.equals(state.get("source_sha256"))
                    && eofLines == ((Number) state.get("next_line")).longValue()) return;
            if (!fingerprint.equals(state.get("source_sha256")) || !"RUNNING".equals(state.get("status"))
                    || eofLines != ((Number) state.get("next_line")).longValue())
                throw new IllegalStateException("Niekompletny import lub inne źródło");
            long accepted = jdbc.queryForObject("select count(*) from catalog.supplier_import_items where run_id=?", Long.class, run);
            long rejected = jdbc.queryForObject("select count(*) from catalog.supplier_import_rejections where run_id=?", Long.class, run);
            if (accepted + rejected != eofLines || (!allowRejected && rejected != 0))
                throw new IllegalStateException("Niezgodność liczników lub wymagana decyzja o kwarantannie");
            jdbc.update("update catalog.supplier_import_runs set status='COMPLETE',expected_rows=?,failure=null where id=?", eofLines, run);
            jdbc.update("insert into catalog.supplier_active_feed(singleton,run_id) values (true,?) on conflict(singleton) do update set run_id=excluded.run_id", run);
        });
    }
    public void fail(UUID run) {
        jdbc.update("update catalog.supplier_import_runs set status='FAILED',failure='INPUT_OR_STORAGE_FAILURE' where id=? and status='RUNNING'", run);
    }
    public void resume(UUID run, String fingerprint) {
        validateFingerprint(fingerprint);
        if (jdbc.update("update catalog.supplier_import_runs set status='RUNNING',failure=null where id=? and source_sha256=? and status='FAILED'", run, fingerprint) != 1)
            throw new IllegalStateException("Nie można wznowić tego importu");
    }
    public void cancel(UUID run) {
        jdbc.update("update catalog.supplier_import_runs set cancelled=true,status='CANCELLED' where id=? and status in ('RUNNING','FAILED')", run);
    }
    /** Adapter odczytu opublikowanego feedu, bez częściowych cen i bez przenikania do Ordering. */
    public Optional<BigDecimal> publishedPrice(String sku) {
        return jdbc.query("select i.amount from catalog.supplier_import_items i join catalog.supplier_active_feed f on f.run_id=i.run_id where i.sku=?",
                (rs, n) -> rs.getBigDecimal(1), sku).stream().findFirst();
    }
    /** Kursor PostgreSQL wymaga transakcji. Nie budujemy listy miliona wierszy w heapie. */
    public void export(UUID run, java.io.Writer output) {
        transaction.executeWithoutResult(tx -> jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("select sku,amount from catalog.supplier_import_items where run_id=? order by line_number")) {
                statement.setObject(1, run); statement.setFetchSize(500);
                try (var rs = statement.executeQuery()) {
                    while (rs.next()) output.write(rs.getString(1) + "," + rs.getBigDecimal(2).toPlainString() + "\n");
                } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            }
            return null;
        }));
    }
    private Map<String,Object> lock(UUID run) {
        return jdbc.queryForMap("select source_sha256,next_line,status from catalog.supplier_import_runs where id=? for update", run);
    }
    private void validateFingerprint(String fingerprint) {
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Wymagany SHA-256");
    }
}
