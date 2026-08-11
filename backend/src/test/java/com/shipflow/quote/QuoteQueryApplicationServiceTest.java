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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuoteQueryApplicationServiceTest {
    private final QuoteMapper mapper = mock(QuoteMapper.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-11T08:00:00Z"), ZoneOffset.UTC);
    private final QuoteQueryApplicationService service =
            new QuoteQueryApplicationService(mapper, new ObjectMapper(), clock);

    @Test
    void listQuotesAppliesTenantAndAllFilters() {
        when(mapper.count(7L, 3L, 4L, "VALID")).thenReturn(1L);
        when(mapper.findPage(7L, 3L, 4L, "VALID", 20, 20)).thenReturn(List.of(quote(11L, "VALID",
                LocalDateTime.of(2026, 8, 12, 8, 0))));

        var result = service.list(7L, 3L, 4L, "VALID", 2, 20);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.destinationCountry()).isEqualTo("US");
            assertThat(item.feeDetail()).containsEntry("baseFee", 12);
            assertThat(item.validTo().getOffset()).isEqualTo(ZoneOffset.UTC);
        });
        verify(mapper).findPage(7L, 3L, 4L, "VALID", 20, 20);
    }

    @Test
    void getQuoteUsesTenantPredicateAndHidesCrossTenantResource() {
        when(mapper.findById(7L, 99L)).thenReturn(null);

        assertThatThrownBy(() -> service.get(7L, 99L))
                .isInstanceOf(QuoteException.class)
                .satisfies(error -> assertThat(((QuoteException) error).code()).isEqualTo("COMMON-1006"));

        verify(mapper).findById(7L, 99L);
    }

    @Test
    void validateQuoteReturnsExpiredWithoutCheckingOrderUsage() {
        when(mapper.findById(7L, 11L)).thenReturn(quote(11L, "VALID",
                LocalDateTime.of(2026, 8, 11, 7, 59, 59)));

        var result = service.validate(7L, 11L);

        assertThat(result.exists()).isTrue();
        assertThat(result.expired()).isTrue();
        assertThat(result.canCreateOrder()).isFalse();
        assertThat(result.reason()).isEqualTo("EXPIRED");
        verify(mapper, never()).hasShipmentOrder(7L, 11L);
    }

    @Test
    void validateQuoteRejectsAlreadyUsedAndAcceptsAvailableQuote() {
        Quote valid = quote(11L, "VALID", LocalDateTime.of(2026, 8, 12, 8, 0));
        when(mapper.findById(7L, 11L)).thenReturn(valid);
        when(mapper.hasShipmentOrder(7L, 11L)).thenReturn(true);
        when(mapper.findById(7L, 12L)).thenReturn(quote(12L, "VALID",
                LocalDateTime.of(2026, 8, 12, 8, 0)));

        assertThat(service.validate(7L, 11L).reason()).isEqualTo("ALREADY_USED");
        assertThat(service.validate(7L, 12L).canCreateOrder()).isTrue();
        verify(mapper).hasShipmentOrder(7L, 11L);
        verify(mapper).hasShipmentOrder(7L, 12L);
    }

    @Test
    void validateQuoteReportsCancelledEvenAfterItsTimeWindowEnds() {
        when(mapper.findById(7L, 13L)).thenReturn(quote(13L, "CANCELLED",
                LocalDateTime.of(2026, 8, 11, 7, 0)));

        var result = service.validate(7L, 13L);

        assertThat(result.expired()).isTrue();
        assertThat(result.canCreateOrder()).isFalse();
        assertThat(result.reason()).isEqualTo("CANCELLED");
        verify(mapper, never()).hasShipmentOrder(7L, 13L);
    }

    @Test
    void listQuotesRejectsOutOfRangePaginationBeforeQuerying() {
        assertThatThrownBy(() -> service.list(7L, null, null, null, 1, 101))
                .isInstanceOf(QuoteException.class)
                .satisfies(error -> assertThat(((QuoteException) error).code()).isEqualTo("COMMON-1001"));
        verify(mapper, never()).count(7L, null, null, null);
    }

    private Quote quote(Long id, String status, LocalDateTime validTo) {
        return new Quote(id, 7L, "Q-" + id, 3L, 4L, 5L, 2,
                new BigDecimal("1.000"), new BigDecimal("10.000"), new BigDecimal("20.000"),
                new BigDecimal("30.000"), new BigDecimal("1.200"), new BigDecimal("1.500"),
                new BigDecimal("12.00"), "USD", "{\"destinationCountry\":\"US\",\"baseFee\":12}",
                LocalDateTime.of(2026, 8, 10, 8, 0), validTo, status, 0L,
                LocalDateTime.of(2026, 8, 10, 8, 0), LocalDateTime.of(2026, 8, 10, 8, 0));
    }
}
