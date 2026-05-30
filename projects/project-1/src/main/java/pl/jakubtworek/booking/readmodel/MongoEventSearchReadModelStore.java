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

@Component
@Profile("nosql-real")
public class MongoEventSearchReadModelStore implements EventSearchReadModelStore {
    private final MongoTemplate mongoTemplate;

    public MongoEventSearchReadModelStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public EventSearchDocument save(EventSearchDocument document) {
        return mongoTemplate.save(document);
    }

    @Override
    public Optional<EventSearchDocument> findByEventId(UUID eventId) {
        return Optional.ofNullable(mongoTemplate.findById(eventId, EventSearchDocument.class));
    }

    @Override
    public List<EventSearchDocument> search(String city, String category, int limit) {
        Query query = new Query()
                .addCriteria(Criteria.where("city").is(city).and("category").is(category))
                .with(Sort.by(Sort.Direction.ASC, "startsAt"))
                .limit(limit);
        return mongoTemplate.find(query, EventSearchDocument.class);
    }

    @Override
    public void deleteAll() {
        mongoTemplate.dropCollection(EventSearchDocument.class);
    }
}
