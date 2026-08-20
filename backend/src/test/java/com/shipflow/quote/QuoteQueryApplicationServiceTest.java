package com.shipflow.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipflow.quote.application.QuoteException;
import com.shipflow.quote.application.QuoteQueryApplicationService;
import com.shipflow.quote.domain.model.Quote;
import com.shipflow.quote.mapper.QuoteMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuoteQueryApplicationServiceTest {
    private static final long TENANT = 7L, USER = 2L;
    private final QuoteMapper mapper = mock(QuoteMapper.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-11T08:00:00Z"), ZoneOffset.UTC);
    private final QuoteQueryApplicationService service = new QuoteQueryApplicationService(mapper, new ObjectMapper(), clock);

    @Test void listUsesCallerScopeAndAllFilters() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 8, 0);
        when(mapper.countForUser(eq(TENANT), eq(USER), eq("Q-"), eq(3L), eq(4L), eq("US"), eq("VALID"), any(), any(), any(), any(), eq(now))).thenReturn(1L);
        when(mapper.findPageForUser(eq(TENANT), eq(USER), eq("Q-"), eq(3L), eq(4L), eq("US"), eq("VALID"), any(), any(), any(), any(), eq(now), eq("validTo"), eq("ASC"), eq(20), eq(20))).thenReturn(List.of(quote(11L, "VALID", LocalDateTime.of(2026, 8, 12, 8, 0))));
        var page = service.list(TENANT, USER, "Q-", 3L, 4L, "US", "VALID", now.minusDays(1), now, now, now.plusDays(1), "validTo", "ASC", 2, 20);
        assertThat(page.items()).singleElement().satisfies(value -> assertThat(value.destinationCountry()).isEqualTo("US"));
        verify(mapper).findPageForUser(eq(TENANT), eq(USER), eq("Q-"), eq(3L), eq(4L), eq("US"), eq("VALID"), any(), any(), any(), any(), eq(now), eq("validTo"), eq("ASC"), eq(20), eq(20));
    }

    @Test void crossScopeDetailIsUnifiedNotFound() {
        when(mapper.findByIdForUser(TENANT, USER, 99L)).thenReturn(null);
        assertThatThrownBy(() -> service.get(TENANT, USER, 99L)).isInstanceOf(QuoteException.class).satisfies(e -> assertThat(((QuoteException) e).code()).isEqualTo("COMMON-1006"));
        verify(mapper).findByIdForUser(TENANT, USER, 99L);
    }

    @Test void validationUsesCallerScopeAndRejectsExpiredQuote() {
        when(mapper.findByIdForUser(TENANT, USER, 11L)).thenReturn(quote(11L, "VALID", LocalDateTime.of(2026, 8, 11, 7, 59)));
        assertThat(service.validate(TENANT, USER, 11L).reason()).isEqualTo("EXPIRED");
        verify(mapper, never()).hasShipmentOrder(TENANT, 11L);
    }

    @Test void rejectsInvalidSortBeforeQuerying() {
        assertThatThrownBy(() -> service.list(TENANT, USER, null, null, null, null, null, null, null, null, null, "unsafe", "DESC", 1, 20)).isInstanceOf(QuoteException.class);
        verify(mapper, never()).countForUser(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private Quote quote(Long id, String status, LocalDateTime validTo) { return new Quote(id, TENANT, "Q-" + id, 3L, 4L, 5L, 2, new BigDecimal("1.000"), new BigDecimal("10.000"), new BigDecimal("20.000"), new BigDecimal("30.000"), new BigDecimal("1.200"), new BigDecimal("1.500"), new BigDecimal("12.00"), "USD", "{\"destinationCountry\":\"US\"}", LocalDateTime.of(2026, 8, 10, 8, 0), validTo, status, 0L, LocalDateTime.of(2026, 8, 10, 8, 0), LocalDateTime.of(2026, 8, 10, 8, 0)); }
}
