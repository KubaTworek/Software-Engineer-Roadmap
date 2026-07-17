package com.example.ecommerce.integration;

import com.example.ecommerce.monitoring.BusinessMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;

/**
 * Wspólny serwis do wykonywania retry dla integracji zewnętrznych.
 *
 * W aplikacji e-commerce integracje zewnętrzne są normalnym źródłem błędów:
 * - provider płatności może chwilowo nie odpowiadać,
 * - ERP może mieć timeout,
 * - WMS może zwrócić błąd sieciowy,
 * - OpenSearch może być chwilowo niedostępny,
 * - serwis e-mail/SMS może odrzucić request.
 *
 * Ten serwis centralizuje obsługę retry, żeby każda integracja
 * nie implementowała własnego mechanizmu ponawiania.
 */
@Service
public class IntegrationRetryService {

    /**
     * Logger techniczny.
     *
     * Logujemy tylko faktyczne ponowienia, a nie każdą próbę.
     * Dzięki temu w logach widać problemy integracyjne bez nadmiernego szumu.
     */
    private static final Logger log = LoggerFactory.getLogger(IntegrationRetryService.class);

    /**
     * Spring RetryTemplate.
     *
     * Zawiera konfigurację retry:
     * - ile razy ponawiać,
     * - jaki backoff zastosować,
     * - dla jakich wyjątków robić retry.
     *
     * Sama konfiguracja powinna być zdefiniowana w RetryConfig.
     */
    private final RetryTemplate retryTemplate;

    /**
     * Metryki biznesowo-techniczne.
     *
     * Pozwalają mierzyć, ile razy system musiał ponawiać wywołania integracji.
     * To ważny sygnał dla observability i stabilności systemu.
     */
    private final BusinessMetrics metrics;

    /**
     * Constructor injection.
     *
     * Serwis dostaje gotowy RetryTemplate oraz komponent metryk.
     */
    public IntegrationRetryService(
            RetryTemplate retryTemplate,
            BusinessMetrics metrics
    ) {
        this.retryTemplate = retryTemplate;
        this.metrics = metrics;
    }

    /**
     * Wykonuje wywołanie integracji z retry i zwraca wynik.
     *
     * Parametry:
     * - integrationName — nazwa integracji używana w logach, np. "erp.sync", "wms.reserve",
     * - callable — właściwa operacja do wykonania.
     *
     * Flow:
     * 1. RetryTemplate uruchamia callable.
     * 2. Jeśli callable rzuci wyjątek obsługiwany przez retry policy, Spring ponowi próbę.
     * 3. Przy każdej ponowionej próbie zwiększamy metrykę retry.
     * 4. Logujemy nazwę integracji i numer próby.
     * 5. Jeśli wszystkie próby się nie powiodą, wyjątek leci dalej do wywołującego.
     *
     * To podejście jest dobre dla błędów przejściowych:
     * - timeout,
     * - chwilowy 503,
     * - reset połączenia,
     * - chwilowy problem sieci.
     *
     * Nie powinno być używane do błędów biznesowych, np. "payment declined",
     * bo ponawianie takiego błędu zwykle nic nie zmieni.
     */
    public <T> T call(String integrationName, Callable<T> callable) {
        try {
            return retryTemplate.execute(context -> {
                /*
                 * Retry count = 0 oznacza pierwszą próbę.
                 *
                 * Metrykę i log zapisujemy dopiero wtedy, gdy to jest rzeczywiste retry,
                 * czyli druga lub kolejna próba.
                 */
                if (context.getRetryCount() > 0) {
                    metrics.integrationRetried();

                    log.warn(
                            "Retrying integration call integration={}, attempt={}",
                            integrationName,
                            context.getRetryCount() + 1
                    );
                }

                /*
                 * Właściwe wywołanie integracji.
                 *
                 * Jeśli callable zakończy się sukcesem, wynik zostanie zwrócony.
                 * Jeśli rzuci wyjątek, RetryTemplate zdecyduje, czy ponowić próbę.
                 */
                return callable.call();
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Wariant dla operacji, które nic nie zwracają.
     *
     * Przykłady:
     * - wysłanie eventu do ERP,
     * - wysłanie rezerwacji do WMS,
     * - wysłanie e-maila,
     * - powiadomienie zewnętrznego systemu.
     *
     * Metoda opakowuje RetryableRunnable w Callable<Void>,
     * żeby użyć tej samej logiki retry co w call().
     */
    public void run(String integrationName, RetryableRunnable runnable) {
        call(integrationName, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Funkcyjny interfejs dla operacji void, które mogą rzucać wyjątek.
     *
     * Standardowy Runnable nie pozwala rzucać checked exceptions.
     * Ten interfejs pozwala pisać proste lambdy dla integracji:
     *
     * retry.run("erp.sync", () -> erpClient.send(payload));
     */
    @FunctionalInterface
    public interface RetryableRunnable {
        void run() throws Exception;
    }
}