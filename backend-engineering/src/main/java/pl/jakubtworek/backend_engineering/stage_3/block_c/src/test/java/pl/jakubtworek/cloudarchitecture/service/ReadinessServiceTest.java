package pl.jakubtworek.cloudarchitecture.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.jakubtworek.cloudarchitecture.service.ReadinessService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadinessServiceTest {

    @Test
    void checksBothDependenciesAndClosesTheBorrowedRedisConnection() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(redis.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenReturn(connection);

        new ReadinessService(jdbc, redis).verifyDependencies();

        verify(jdbc).queryForObject("SELECT 1", Integer.class);
        verify(connection).ping();
        verify(connection).close();
    }
}
