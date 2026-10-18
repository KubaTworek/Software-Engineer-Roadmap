package com.ridesharing.mvp.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridesharing.mvp.user.AppUser;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class IdempotencyServiceTest {
    @Test
    void failedBusinessOperationPersistsFailedClaimOutsideItsRollback() {
        var repository = mock(IdempotencyKeyRepository.class);
        var claims = mock(IdempotencyClaimService.class);
        var service = new IdempotencyService(repository, new ObjectMapper(), claims);
        var user = AppUser.builder().id(UUID.randomUUID()).build();
        UUID claimId = UUID.randomUUID();
        when(repository.findByKeyAndUserIdAndEndpoint("key-1", user.getId(), "POST /rides"))
                .thenReturn(Optional.empty());
        when(claims.claimNew(eq("key-1"), eq(user), eq("POST /rides"), anyString()))
                .thenReturn(claimId);

        assertThatThrownBy(() -> service.execute(
                "key-1", user, "POST /rides", new Request("A"), String.class,
                () -> { throw new IllegalStateException("domain failure"); }
        )).isInstanceOf(IllegalStateException.class);

        verify(claims).fail(claimId);
        verify(claims, never()).complete(any(), anyString(), anyInt());
    }

    private record Request(String value) {}
}
