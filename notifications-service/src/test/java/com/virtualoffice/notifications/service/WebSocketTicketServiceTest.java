package com.virtualoffice.notifications.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebSocketTicketServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private WebSocketTicketService service;

    @BeforeEach
    void wireFields() {
        ReflectionTestUtils.setField(service, "ticketPrefix", "notif:ws-ticket:");
        ReflectionTestUtils.setField(service, "ttlSeconds", 60L);
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void createTicketReturnsNonBlankUuidAndWritesToRedis() {
        String ticket = service.createTicket(42L);

        assertThat(ticket).isNotBlank();
        verify(valueOps).set(eq("notif:ws-ticket:" + ticket), eq("42"), any());
    }

    @Test
    void createTicketProducesDifferentTicketsEachCall() {
        String t1 = service.createTicket(1L);
        String t2 = service.createTicket(1L);
        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    void consumeTicketReturnsUserIdOnHit() {
        when(valueOps.getAndDelete("notif:ws-ticket:abc")).thenReturn("99");

        Optional<Long> userId = service.consumeTicket("abc");

        assertThat(userId).hasValue(99L);
    }

    @Test
    void consumeTicketReturnsEmptyOnMiss() {
        when(valueOps.getAndDelete(anyString())).thenReturn(null);

        Optional<Long> userId = service.consumeTicket("not-there");

        assertThat(userId).isEmpty();
    }

    @Test
    void consumeTicketReturnsEmptyForBlankInput() {
        assertThat(service.consumeTicket(null)).isEmpty();
        assertThat(service.consumeTicket("")).isEmpty();
        assertThat(service.consumeTicket("   ")).isEmpty();

        verify(valueOps, never()).getAndDelete(any());
    }

    @Test
    void consumeTicketReturnsEmptyOnMalformedValue() {
        when(valueOps.getAndDelete(anyString())).thenReturn("not-a-number");

        Optional<Long> userId = service.consumeTicket("abc");

        assertThat(userId).isEmpty();
    }

    @Test
    void ticketsAreSingleUse() {
        when(valueOps.getAndDelete("notif:ws-ticket:once"))
                .thenReturn("7")    // first call: hit
                .thenReturn(null);  // second call: miss (already deleted)

        assertThat(service.consumeTicket("once")).hasValue(7L);
        assertThat(service.consumeTicket("once")).isEmpty();
    }
}
