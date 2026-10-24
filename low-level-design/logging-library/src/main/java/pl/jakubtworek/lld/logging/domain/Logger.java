package pl.jakubtworek.lld.logging.domain;

import java.io.IOException;
import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;
import static pl.jakubtworek.lld.logging.domain.Delivery.Status.*;

/**
 * Koordynator: kwalifikuje trasy, raz przygotowuje zdarzenie i format, potem
 * wykonuje synchroniczny fan-out w zadanej kolejności. Konfiguracja jest stała.
 * Instancja wraz z sinkami jest obsługiwana w jednym wątku; własne zależności
 * mają ochronę przed ponownym wejściem w ten sam logger. Flaga active nie jest
 * blokadą wątkową; zagnieżdżone odfiltrowane wywołanie też zostanie odrzucone.
 */
public final class Logger {

    private final String name;
    private final LogLevel threshold;
    private final LogFormatter formatter;
    private final List<LogTarget> targets;
    private final FailurePolicy failurePolicy;
    private final Clock clock;
    private final ContextMasker masker;
    private boolean active;
    private final EnumMap<LogLevel, BitSet> routes = new EnumMap<>(LogLevel.class);
    private final LogResult filtered;

    public Logger(String name, LogLevel threshold, LogFormatter formatter, List<LogTarget> targets,
            FailurePolicy failurePolicy, Clock clock) {
        this(name, threshold, formatter, targets, failurePolicy, clock, ContextMasker.identity());
    }

    public Logger(String name, LogLevel threshold, LogFormatter formatter, List<LogTarget> targets,
            FailurePolicy failurePolicy, Clock clock, ContextMasker masker) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Pusta nazwa loggera");
        this.masker = Objects.requireNonNull(masker);
        this.name = name;
        this.threshold = Objects.requireNonNull(threshold);
        this.formatter = Objects.requireNonNull(formatter);
        this.targets = List.copyOf(targets);
        this.failurePolicy = Objects.requireNonNull(failurePolicy);
        this.clock = Objects.requireNonNull(clock);
        var names = new HashSet<String>();
        if (targets.isEmpty())
            throw new IllegalArgumentException("Wymagane co najmniej jedno wyjście");
        for (var target : this.targets) {
            if (!names.add(target.name()))
                throw new IllegalArgumentException("Powtórzona nazwa wyjścia: " + target.name());
        }
        // Konfiguracja jest niezmienna: kompilujemy progi raz do masek tras.
        // Raport wszystkich T wyjść nadal kosztuje O(T); maska usuwa powtarzane
        // porównania progów i pozwala odrzucić całe zdarzenie w O(1).
        for (LogLevel level : LogLevel.values()) {
            var route = new BitSet(this.targets.size());
            if (level.atLeast(threshold)) {
                for (int i = 0; i < this.targets.size(); i++) {
                    if (level.atLeast(this.targets.get(i).threshold())) {
                        route.set(i);
                    }
                }
            }
            routes.put(level, route);
        }
        filtered = new LogResult(Optional.empty(), this.targets.stream().map(target -> new Delivery(target.name(),
                FILTERED, Optional.empty())).toList());
    }

    public LogResult log(LogLevel level, String message) {
        Objects.requireNonNull(message);
        return log(level, () -> message);
    }

    public LogResult log(LogLevel level, Supplier<String> message) {
        return log(level, LogContext.empty(), message);
    }

    public LogResult log(LogLevel level, LogContext context, String message) {
        Objects.requireNonNull(message);
        return log(level, context, () -> message);
    }

    public LogResult log(LogLevel level, LogContext context, Supplier<String> message) {
        // Monitor synchronized też dopuszcza ponowne wejście tego samego
        // wątku. Ta flaga pilnuje innej reguły; sama nie jest blokadą wątkową.
        if (active)
            throw new ReentrantLoggingException();
        active = true;
        try {
            return dispatch(level, context, message);
        } finally {
            // Także po Error, null i awarii przygotowania.
            active = false;
        }
    }

    private LogResult dispatch(LogLevel level, LogContext context, Supplier<String> message) {
        Objects.requireNonNull(level);
        Objects.requireNonNull(context);
        Objects.requireNonNull(message);
        BitSet route = routes.get(level);
        if (route.isEmpty()) {
            return filtered;
        }
        // Żadne wyjście nie jest dotykane, dopóki całość przygotowania nie powiedzie się.
        // Supplier uruchamiamy dopiero po filtrze i tylko raz dla wszystkich
        // odbiorców. Wariant ze String obliczył argument już przed wejściem tutaj.
        String text = Objects.requireNonNull(message.get(), "Supplier zwrócił null");
        LogContext masked = Objects.requireNonNull(masker.mask(context), "Masker zwrócił null");
        var event = new LogEvent(clock.instant(), level, name, text, masked);
        String formatted = Objects.requireNonNull(formatter.format(event), "Formatter zwrócił null");
        var deliveries = new ArrayList<Delivery>();
        boolean stopped = false;
        for (int index = 0; index < targets.size(); index++) {
            var target = targets.get(index);
            if (!route.get(index)) {
                deliveries.add(new Delivery(target.name(), FILTERED, Optional.empty()));
            } else if (stopped) {
                deliveries.add(new Delivery(target.name(), NOT_ATTEMPTED, Optional.empty()));
            } else {
                try {
                    target.sink().write(formatted);
                    deliveries.add(new Delivery(target.name(), WRITTEN, Optional.empty()));
                } catch (IOException | RuntimeException ex) {
                    DeliveryFailure failure = DeliveryFailure.from(ex);
                    deliveries.add(new Delivery(target.name(), FAILED, Optional.of(failure)));
                    stopped = failurePolicy == FailurePolicy.STOP_ON_FAILURE;
                }
            }
        }
        return new LogResult(Optional.of(event), deliveries);
    }
}
