package pl.jakubtworek.backend_systems_lab_stage_1.block_c.bean;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FINAL classes cannot be proxied by CGLIB.
 */

// final
@Service
public class FinalService {

    /**
     * FINAL methods also cannot be overridden
     * by CGLIB proxy.
     */

    // final
    @Transactional
    public void test() {

        System.out.println("Will not be proxied correctly");
    }
}