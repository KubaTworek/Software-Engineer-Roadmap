package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.race_condition;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/race")
public class RaceConditionController {

    private final ConcurrencyTestService testService = new ConcurrencyTestService();

    @PostMapping("/broken")
    public Result broken(@RequestParam(defaultValue = "100") int threads) throws InterruptedException {
        return testService.runTest(new BrokenTicketStore(), threads);
    }

    @PostMapping("/synchronized")
    public Result synchronizedVersion(@RequestParam(defaultValue = "100") int threads) throws InterruptedException {
        return testService.runTest(new SynchronizedTicketStore(), threads);
    }

    @PostMapping("/atomic")
    public Result atomic(@RequestParam(defaultValue = "100") int threads) throws InterruptedException {
        return testService.runTest(new AtomicTicketStore(), threads);
    }

    @PostMapping("/lock")
    public Result lock(@RequestParam(defaultValue = "100") int threads) throws InterruptedException {
        return testService.runTest(new LockTicketStore(), threads);
    }

    @PostMapping("/single-thread")
    public Result singleThread(@RequestParam(defaultValue = "100") int threads) throws InterruptedException {
        return testService.runTest(new SingleThreadTicketStore(), threads);
    }
}