package pl.jakubtworek.backend_systems_lab_stage_1.block_c.transactional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demonstrates proxy limitations.
 *
 * CGLIB cannot proxy final classes or final methods,
 * because it creates subclasses at runtime.
 */
@Service
public class FinalMethodProblemService {

    /**
     * This method may not be proxied correctly if CGLIB is used,
     * because final methods cannot be overridden.
     */
    @Transactional
    public final void finalTransactionalMethod() {
        System.out.println("This final method is problematic for proxying");
    }

    /**
     * This method is proxy-friendly.
     */
    @Transactional
    public void normalTransactionalMethod() {
        System.out.println("This method can be proxied");
    }
}