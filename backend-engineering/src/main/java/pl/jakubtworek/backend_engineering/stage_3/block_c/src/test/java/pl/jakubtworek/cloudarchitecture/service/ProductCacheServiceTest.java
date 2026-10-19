package pl.jakubtworek.cloudarchitecture.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import pl.jakubtworek.cloudarchitecture.dto.ProductDto;
import pl.jakubtworek.cloudarchitecture.service.ProductCacheService;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked") // Mockito creates generic Redis test doubles through type erasure.
class ProductCacheServiceTest {

    @Test
    void readsAndWritesAProductUsingTheSharedKeyAndTtl() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        ObjectMapper mapper = new ObjectMapper();
        ProductDto product = new ProductDto(12L, "Keyboard", new BigDecimal("199.99"));
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("product:12")).thenReturn(mapper.writeValueAsString(product));
        ProductCacheService cache = new ProductCacheService(redis, mapper);

        assertThat(cache.get(12L)).contains(product);
        cache.put(product);

        verify(values).set("product:12", mapper.writeValueAsString(product), Duration.ofSeconds(60));
    }

    @Test
    void treatsCorruptedOrUnavailableCacheAsAMiss() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("product:12")).thenReturn("not-json");

        assertThat(new ProductCacheService(redis, new ObjectMapper()).get(12L)).isEmpty();
        verify(redis).delete("product:12");
    }

    @Test
    void rejectsInvalidIdentifiersInsteadOfCreatingSharedNullKeys() {
        ProductCacheService cache = new ProductCacheService(
                mock(StringRedisTemplate.class),
                new ObjectMapper()
        );

        assertThatIllegalArgumentException().isThrownBy(() -> cache.get(null));
        assertThatIllegalArgumentException().isThrownBy(() -> cache.evict(0L));
    }
}
