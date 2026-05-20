package pl.jakubtworek.backend_engineering.stage_1.block_c.bean;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * No interface implementation.
 *
 * Spring will use CGLIB proxy here.
 */
@Service
public class CglibExampleService {

    @Transactional
    public void execute() {

        System.out.println("CGLIB proxy method");
    }
}