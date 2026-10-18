package pl.jakubtworek.booking.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stabilny kontrakt HTTP dla offset pagination.
 *
 * <p>{@link Page} jest typem infrastrukturalnym Spring Data, a układ JSON klasy
 * {@code PageImpl} nie jest gwarantowanym kontraktem publicznym. Mapowanie na
 * własne DTO oddziela API od wersji biblioteki, zachowując jednocześnie koszt
 * {@code COUNT(*)}, który laboratorium ma pokazać.</p>
 */
public record OffsetPageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static <T> OffsetPageResponse<T> from(Page<T> source) {
        return new OffsetPageResponse<>(
                List.copyOf(source.getContent()),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.hasNext()
        );
    }
}
