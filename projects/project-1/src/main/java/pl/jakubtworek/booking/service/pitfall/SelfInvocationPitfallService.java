package pl.jakubtworek.booking.service.pitfall;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Serwis edukacyjny pokazujący problem self-invocation w Springu.
 *
 * Self-invocation oznacza sytuację, w której metoda jednej klasy wywołuje inną
 * metodę tej samej klasy przez this.
 *
 * Problem:
 * adnotacje takie jak @Transactional są zwykle obsługiwane przez proxy Springa.
 * Jeśli wywołanie nie przejdzie przez proxy, Spring nie ma okazji uruchomić
 * dodatkowej logiki, np. rozpoczęcia transakcji.
 *
 * Ta klasa pokazuje różnicę między:
 *
 * - wywołaniem transactionalMethod() przez this,
 * - wywołaniem transactionalMethod() przez proxy Springa.
 */
@Service
public class SelfInvocationPitfallService {

    /**
     * ObjectProvider pozwala pobrać beana z kontenera Springa dopiero wtedy,
     * gdy jest potrzebny.
     *
     * Tutaj używamy go celowo, żeby pobrać proxy tej samej klasy.
     *
     * Gdybyśmy zwyczajnie zrobili:
     *
     * private final SelfInvocationPitfallService self;
     *
     * moglibyśmy łatwo wejść w problem cyklicznej zależności.
     *
     * ObjectProvider jest leniwy, więc proxy jest pobierane dopiero w metodzie
     * callTransactionalMethodThroughProxy().
     */
    private final ObjectProvider<SelfInvocationPitfallService> selfProvider;

    /**
     * Constructor injection.
     *
     * Wstrzykujemy ObjectProvider, a nie bezpośrednio SelfInvocationPitfallService,
     * żeby uniknąć problemów z cyklicznym tworzeniem beana.
     */
    public SelfInvocationPitfallService(ObjectProvider<SelfInvocationPitfallService> selfProvider) {
        this.selfProvider = selfProvider;
    }

    /**
     * Wywołuje metodę oznaczoną @Transactional przez this.
     *
     * To jest celowo błędny wariant.
     *
     * Wywołanie:
     *
     * this.transactionalMethod()
     *
     * nie przechodzi przez proxy Springa.
     *
     * Efekt:
     * @Transactional na transactionalMethod() może zostać pominięte.
     *
     * Oczekiwany wynik edukacyjny:
     * TransactionSynchronizationManager.isActualTransactionActive() zwróci false.
     */
    public boolean callTransactionalMethodThroughThis() {
        return transactionalMethod();
    }

    /**
     * Wywołuje metodę oznaczoną @Transactional przez proxy Springa.
     *
     * selfProvider.getObject() zwraca obiekt zarządzany przez Springa,
     * czyli w praktyce proxy tej klasy.
     *
     * Wywołanie przechodzi przez proxy, więc Spring może obsłużyć @Transactional.
     *
     * Oczekiwany wynik edukacyjny:
     * TransactionSynchronizationManager.isActualTransactionActive() zwróci true.
     */
    public boolean callTransactionalMethodThroughProxy() {
        return selfProvider.getObject().transactionalMethod();
    }

    /**
     * Metoda transakcyjna używana do eksperymentu.
     *
     * Propagation.REQUIRES_NEW oznacza:
     *
     * - zawsze rozpocznij nową transakcję,
     * - jeśli jakaś transakcja już istnieje, zawieś ją i użyj nowej.
     *
     * W tym przykładzie REQUIRES_NEW pomaga wyraźnie pokazać, czy proxy faktycznie
     * uruchomiło mechanizm transakcyjny.
     *
     * TransactionSynchronizationManager.isActualTransactionActive() sprawdza,
     * czy w bieżącym wątku Spring widzi aktywną transakcję.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean transactionalMethod() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }
}