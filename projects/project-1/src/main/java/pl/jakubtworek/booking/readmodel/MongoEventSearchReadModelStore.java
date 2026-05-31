package pl.jakubtworek.booking.readmodel;

import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementacja EventSearchReadModelStore oparta o MongoDB.
 *
 * Ta klasa jest aktywna tylko dla profilu:
 *
 * nosql-real
 *
 * Dzięki temu domyślne testy i zwykłe uruchomienie aplikacji nie wymagają
 * działającego MongoDB. W profilu testowym można używać implementacji in-memory.
 *
 * To jest część etapu NoSQL/cache:
 *
 * - PostgreSQL nadal jest źródłem prawdy,
 * - MongoDB przechowuje denormalizowany read model,
 * - dokument EventSearchDocument jest zoptymalizowany pod szybki odczyt/search.
 */
@Component
@Profile("nosql-real")
public class MongoEventSearchReadModelStore implements EventSearchReadModelStore {

    /**
     * MongoTemplate daje niższopoziomową kontrolę nad operacjami MongoDB
     * niż typowe Spring Data MongoRepository.
     *
     * W tym projekcie jest wygodny, bo chcemy pokazać jawne:
     *
     * - save dokumentu,
     * - findById,
     * - query po polach city/category,
     * - sortowanie,
     * - limit,
     * - dropCollection.
     */
    private final MongoTemplate mongoTemplate;

    /**
     * Constructor injection.
     *
     * MongoTemplate jest dostarczany przez Spring Boot auto-configuration,
     * ale tylko wtedy, gdy aplikacja ma zależności Mongo i aktywną konfigurację.
     */
    public MongoEventSearchReadModelStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Zapisuje dokument read modelu w MongoDB.
     *
     * mongoTemplate.save(...) działa jak upsert:
     *
     * - jeśli dokument o danym ID nie istnieje, zostanie utworzony,
     * - jeśli dokument już istnieje, zostanie nadpisany.
     *
     * To dobrze pasuje do rebuilda read modelu, bo odbudowa dokumentu powinna
     * zastąpić jego poprzednią wersję.
     */
    @Override
    public EventSearchDocument save(EventSearchDocument document) {
        return mongoTemplate.save(document);
    }

    /**
     * Pobiera dokument read modelu po eventId.
     *
     * Zakładamy, że identyfikator dokumentu MongoDB odpowiada eventId.
     *
     * Optional jest użyty po to, żeby warstwa serwisowa mogła jawnie obsłużyć
     * brak dokumentu i zamienić go np. na NotFoundException.
     */
    @Override
    public Optional<EventSearchDocument> findByEventId(UUID eventId) {
        return Optional.ofNullable(mongoTemplate.findById(eventId, EventSearchDocument.class));
    }

    /**
     * Wyszukuje eventy w read modelu po city i category.
     *
     * To jest przykład query zaprojektowanego pod access pattern:
     *
     * "pokaż eventy w danym mieście i kategorii, posortowane po startsAt".
     *
     * W MongoDB warto mieć indeks odpowiadający temu zapytaniu, np.:
     *
     * db.eventSearchDocument.createIndex({
     *   city: 1,
     *   category: 1,
     *   startsAt: 1
     * })
     *
     * Bez indeksu MongoDB będzie musiał skanować więcej dokumentów.
     */
    @Override
    public List<EventSearchDocument> search(String city, String category, int limit) {
        Query query = new Query()
                /*
                 * Criteria.where("city").is(city).and("category").is(category)
                 * oznacza:
                 *
                 * city == podane city
                 * AND category == podana category
                 */
                .addCriteria(Criteria.where("city").is(city).and("category").is(category))

                /*
                 * Sortujemy rosnąco po startsAt, czyli najbliższe eventy będą pierwsze.
                 */
                .with(Sort.by(Sort.Direction.ASC, "startsAt"))

                /*
                 * Limit ogranicza liczbę zwracanych dokumentów.
                 *
                 * Uwaga: ta metoda zakłada, że limit został wcześniej zwalidowany
                 * albo ograniczony w serwisie. Jeśli nie, warto dodać zabezpieczenie
                 * przed limit=100000.
                 */
                .limit(limit);

        return mongoTemplate.find(query, EventSearchDocument.class);
    }

    /**
     * Usuwa całą kolekcję read modelu.
     *
     * To jest operacja edukacyjna/testowa.
     *
     * Pokazuje, że read model jest odtwarzalny z PostgreSQL.
     *
     * Produkcyjnie dropCollection jest bardzo niebezpieczne i powinno być:
     *
     * - niedostępne z publicznego API,
     * - ograniczone do administratora,
     * - albo wykonywane tylko przez kontrolowany job techniczny.
     */
    @Override
    public void deleteAll() {
        mongoTemplate.dropCollection(EventSearchDocument.class);
    }
}