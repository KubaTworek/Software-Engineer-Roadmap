package pl.jakubtworek.marketplace.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.jakubtworek.marketplace.catalog.infrastructure.SupplierCatalogImport;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SupplierCatalogImportIntegrationTest extends AbstractIntegrationTest {
    @Test void publishesOnlyAtVerifiedEofAndExportsAcceptedRows(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        UUID run = UUID.randomUUID();
        var file = directory.resolve("feed.csv");
        java.nio.file.Files.writeString(file, "sku-good,12.00\ninvalid\n");
        var loader = new pl.jakubtworek.marketplace.catalog.infrastructure.SupplierFeedFile(importer());
        assertThrows(IllegalStateException.class, () -> loader.load(run, file, false));
        assertTrue(importer().publishedPrice("sku-good").isEmpty());
        assertEquals("FAILED", jdbcTemplate.queryForObject("select status from catalog.supplier_import_runs where id=?", String.class, run));
        String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(file)));
        importer().resume(run, digest);
        // Nowy adapter czyta wejście ponownie i weryfikuje treść już zatwierdzonej porcji.
        new pl.jakubtworek.marketplace.catalog.infrastructure.SupplierFeedFile(importer()).load(run, file, true);
        assertEquals(new BigDecimal("12.00"), importer().publishedPrice("sku-good").orElseThrow());
        loader.load(run, file, true); // Retry COMPLETE sprawdza zawartość, nie publikuje ponownie.
        var output = new java.io.StringWriter();
        importer().export(run, output);
        assertEquals("sku-good,12.00\n", output.toString());
        importer().cancel(run); // COMPLETE jest terminalny, cancel nie wycofuje publikacji.
        assertEquals("COMPLETE", jdbcTemplate.queryForObject("select status from catalog.supplier_import_runs where id=?", String.class, run));
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from catalog.supplier_import_rejections where run_id=?", Integer.class, run));
    }
    private SupplierCatalogImport importer() {
        return new SupplierCatalogImport(jdbcTemplate,
                new TransactionTemplate(new DataSourceTransactionManager(jdbcTemplate.getDataSource())));
    }
    @Test void failedChunkRollsBackRowsAndCheckpointAndNewInstanceCanResume() {
        UUID run = UUID.randomUUID();
        String digest = "a".repeat(64);
        var first = new SupplierCatalogImport.Row("sku-1", BigDecimal.TEN);
        assertEquals(1, importer().append(run, digest, 0, List.of(first)));
        // Powtórzony SKU w nowej porcji powoduje konflikt UNIQUE po pierwszym insercie.
        assertThrows(RuntimeException.class, () -> importer().append(run, digest, 1, List.of(
                new SupplierCatalogImport.Row("sku-2", BigDecimal.ONE), first)));
        assertEquals(1L, jdbcTemplate.queryForObject("select next_line from catalog.supplier_import_runs where id=?", Long.class, run));
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from catalog.supplier_import_items where run_id=?", Integer.class, run));
        assertEquals(2, importer().append(run, digest, 1, List.of(new SupplierCatalogImport.Row("sku-2", BigDecimal.ONE))));
        assertEquals(2, importer().append(run, digest, 0, List.of(first))); // retry po utracie odpowiedzi
        assertThrows(IllegalStateException.class, () -> importer().append(run, "b".repeat(64), 2, List.of(first)));
        importer().cancel(run);
        assertThrows(IllegalStateException.class, () -> importer().append(run, digest, 2, List.of(first)));
    }
}
