package pl.jakubtworek.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.jakubtworek.booking.entity.Event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
    @Query("""
            select event
              from Event event
             where event.city = :city
               and event.category = :category
               and event.startsAt >= :from
             order by event.startsAt asc
            """)
    List<Event> searchByCityCategoryAndFrom(
            @Param("city") String city,
            @Param("category") String category,
            @Param("from") OffsetDateTime from
    );
}
