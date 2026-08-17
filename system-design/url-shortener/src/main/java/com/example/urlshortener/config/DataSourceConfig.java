package com.example.urlshortener.config;

import com.zaxxer.hikari.HikariDataSource;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

/**
 * Konfiguracja źródeł danych dla trybu z read replica.
 *
 * <p>
 * Ta klasa jest aktywowana tylko wtedy, gdy w konfiguracji aplikacji ustawiono:
 * </p>
 *
 * <pre>
 * app.datasource.read-replica-enabled=true
 * </pre>
 *
 * <p>
 * Jej zadaniem jest utworzenie dwóch osobnych pul połączeń:
 * </p>
 *
 * <ul>
 *     <li>write datasource — połączenia do głównej bazy, obsługującej zapisy,</li>
 *     <li>read datasource — połączenia do repliki, obsługującej odczyty.</li>
 * </ul>
 *
 * <p>
 * Następnie oba źródła danych są podpinane pod {@link ReadWriteRoutingDataSource},
 * który decyduje, czy dana operacja powinna trafić do primary database czy do
 * read replica.
 * </p>
 *
 * <p>
 * Typowy mechanizm routingu opiera się o transakcję Springa:
 * </p>
 *
 * <ul>
 *     <li>{@code @Transactional(readOnly = true)} → READ datasource,</li>
 *     <li>{@code @Transactional} bez {@code readOnly = true} → WRITE datasource.</li>
 * </ul>
 *
 * <p>
 * Dzięki temu kod aplikacji zwykle nie musi ręcznie wybierać bazy. Wystarczy
 * poprawnie oznaczać metody transakcyjne.
 * </p>
 */
@Configuration
@ConditionalOnProperty(
        prefix = "app.datasource",
        name = "read-replica-enabled",
        havingValue = "true"
)
public class DataSourceConfig {

    /**
     * Tworzy główny bean {@link DataSource} używany przez aplikację.
     *
     * <p>
     * Bean jest oznaczony jako {@link Primary}, ponieważ Spring Boot, JPA,
     * Flyway, JdbcTemplate i inne komponenty mogą oczekiwać jednego głównego
     * {@code DataSource}. Dzięki {@code @Primary} ten bean będzie domyślnie
     * wybierany, jeśli w kontekście istnieje więcej niż jedno źródło danych.
     * </p>
     *
     * <p>
     * Metoda wykonuje następujące kroki:
     * </p>
     *
     * <ol>
     *     <li>Tworzy datasource do zapisu, czyli {@code writeDataSource}.</li>
     *     <li>Tworzy datasource do odczytu, czyli {@code readDataSource}.</li>
     *     <li>Tworzy routing datasource.</li>
     *     <li>Rejestruje datasource WRITE i READ jako targety routingu.</li>
     *     <li>Ustawia WRITE jako domyślne źródło danych.</li>
     *     <li>Opakowuje routing datasource w {@link LazyConnectionDataSourceProxy}.</li>
     * </ol>
     *
     * @param properties konfiguracja adresów, loginów i haseł dla primary oraz repliki
     * @return główny datasource aplikacji z routingiem read/write
     */
    @Bean
    @Primary
    DataSource dataSource(ReadReplicaProperties properties) {

        /*
         * Tworzymy pulę połączeń do głównej bazy danych.
         *
         * Ta baza powinna obsługiwać wszystkie operacje zapisu:
         *
         * - tworzenie short URL,
         * - blokowanie linków,
         * - zapis click events,
         * - aktualizacja agregatów,
         * - migracje schematu.
         */
        DataSource writeDataSource = hikari(
                properties.writeUrl(),
                properties.writeUsername(),
                properties.writePassword(),
                "write-pool"
        );

        /*
         * Tworzymy pulę połączeń do repliki odczytowej.
         *
         * Ta baza powinna obsługiwać zapytania read-only, np.:
         *
         * - dashboard,
         * - odczyt szczegółów URL,
         * - część lookupów, jeśli akceptujemy replication lag.
         */
        DataSource readDataSource = hikari(
                properties.readUrl(),
                properties.readUsername(),
                properties.readPassword(),
                "read-pool"
        );

        /*
         * Tworzymy routing datasource.
         *
         * ReadWriteRoutingDataSource prawdopodobnie rozszerza
         * AbstractRoutingDataSource i nadpisuje determineCurrentLookupKey().
         *
         * To właśnie tam zapada decyzja, czy aktualny request/transakcja
         * powinna użyć DataSourceType.READ czy DataSourceType.WRITE.
         */
        ReadWriteRoutingDataSource routingDataSource = new ReadWriteRoutingDataSource();

        /*
         * Przygotowujemy mapę target datasource.
         *
         * Klucze mapy muszą być zgodne z wartościami zwracanymi przez
         * ReadWriteRoutingDataSource.determineCurrentLookupKey().
         */
        Map<Object, Object> targets = new HashMap<>();

        /*
         * DataSourceType.WRITE wskazuje na primary database.
         */
        targets.put(DataSourceType.WRITE, writeDataSource);

        /*
         * DataSourceType.READ wskazuje na read replica.
         */
        targets.put(DataSourceType.READ, readDataSource);

        /*
         * Podpinamy mapę targetów do routingu.
         */
        routingDataSource.setTargetDataSources(targets);

        /*
         * Ustawiamy domyślne źródło danych jako WRITE.
         *
         * To bezpieczny fallback. Jeśli routing nie będzie w stanie ustalić
         * typu datasource, request trafi do primary database.
         *
         * Lepiej przypadkiem wykonać odczyt na primary niż przypadkiem wykonać
         * zapis na replice.
         */
        routingDataSource.setDefaultTargetDataSource(writeDataSource);

        /*
         * Inicjalizujemy routing datasource po ustawieniu targetów.
         *
         * AbstractRoutingDataSource wymaga wywołania afterPropertiesSet(),
         * żeby przetworzyć mapę targetów do wewnętrznej struktury.
         */
        routingDataSource.afterPropertiesSet();

        /*
         * Opakowujemy routing datasource w LazyConnectionDataSourceProxy.
         *
         * To bardzo ważne przy routingu read/write.
         *
         * Bez LazyConnectionDataSourceProxy połączenie mogłoby zostać pobrane
         * z puli zbyt wcześnie — zanim Spring ustawi kontekst transakcji
         * i zanim routing będzie wiedział, czy transakcja jest read-only.
         *
         * LazyConnectionDataSourceProxy opóźnia faktyczne pobranie połączenia
         * aż do momentu pierwszego użycia. Dzięki temu routing ma większą szansę
         * wybrać właściwy datasource na podstawie aktualnej transakcji.
         */
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }

    /**
     * Tworzy pojedynczy {@link HikariDataSource}.
     *
     * <p>
     * HikariCP jest domyślnym i bardzo popularnym connection poolem w Spring Boot.
     * Ta metoda pomocnicza konfiguruje podstawowe parametry puli:
     * </p>
     *
     * <ul>
     *     <li>JDBC URL,</li>
     *     <li>username,</li>
     *     <li>password,</li>
     *     <li>nazwę puli,</li>
     *     <li>maksymalny rozmiar puli,</li>
     *     <li>minimalną liczbę idle connections.</li>
     * </ul>
     *
     * @param url JDBC URL bazy danych
     * @param username nazwa użytkownika bazy
     * @param password hasło użytkownika bazy
     * @param poolName nazwa puli połączeń
     * @return skonfigurowany datasource HikariCP
     */
    private DataSource hikari(
            String url,
            String username,
            String password,
            String poolName
    ) {
        /*
         * Budujemy HikariDataSource przy pomocy Springowego DataSourceBuildera.
         *
         * type(HikariDataSource.class) wymusza konkretną implementację puli.
         */
        HikariDataSource dataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(url)
                .username(username)
                .password(password)
                .build();

        /*
         * Ustawiamy czytelną nazwę puli.
         *
         * Przydatne w logach, metrykach, thread dumpach i narzędziach obserwowalności.
         */
        dataSource.setPoolName(poolName);

        /*
         * Ustawiamy maksymalny rozmiar puli.
         *
         * Dla read-pool ustawiono większy limit, bo system URL shortener jest
         * zazwyczaj read-heavy. Redirectów i zapytań dashboardowych jest zwykle
         * więcej niż operacji zapisu.
         *
         * write-pool: 10 połączeń
         * read-pool:  20 połączeń
         */
        dataSource.setMaximumPoolSize(poolName.equals("write-pool") ? 10 : 20);

        /*
         * Minimalna liczba bezczynnych połączeń utrzymywanych w puli.
         *
         * Dzięki temu aplikacja ma kilka gotowych połączeń i nie musi otwierać
         * nowego connection przy każdym nagłym requestcie.
         */
        dataSource.setMinimumIdle(2);

        /*
         * Zwracamy skonfigurowany datasource.
         */
        return dataSource;
    }
}