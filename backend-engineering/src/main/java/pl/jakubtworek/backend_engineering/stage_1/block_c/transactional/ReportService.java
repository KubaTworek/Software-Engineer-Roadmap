package pl.jakubtworek.backend_engineering.stage_1.block_c.transactional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demonstrates transaction isolation level.
 *
 * Isolation controls how visible changes from other transactions are.
 */
@Service
public class ReportService {

    /**
     * READ_COMMITTED prevents dirty reads.
     *
     * It is a common default in many databases.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void generateStandardReport() {
        System.out.println("Generating report with READ_COMMITTED isolation");
    }

    /**
     * SERIALIZABLE provides the strongest consistency,
     * but may reduce performance because of stronger locking.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void generateCriticalFinancialReport() {
        System.out.println("Generating report with SERIALIZABLE isolation");
    }
}