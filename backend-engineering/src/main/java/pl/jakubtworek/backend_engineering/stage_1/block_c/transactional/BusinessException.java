package pl.jakubtworek.backend_engineering.stage_1.block_c.transactional;

/**
 * Checked exception used to demonstrate rollback rules.
 *
 * By default, checked exceptions do NOT trigger rollback in Spring.
 */
public class BusinessException extends Exception {

    public BusinessException(String message) {
        super(message);
    }
}