package com.shipflow.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipflow.logistics.domain.model.PriceRuleTier;
import com.shipflow.logistics.domain.model.PublishedPriceRule;
import com.shipflow.quote.api.model.CreateQuoteRequest;
import com.shipflow.quote.application.QuoteCreationApplicationService;
import com.shipflow.quote.application.QuoteException;
import com.shipflow.quote.application.QuoteQueryApplicationService;
import com.shipflow.quote.domain.model.Quote;
import com.shipflow.quote.mapper.QuoteAuditMapper;
import com.shipflow.quote.mapper.QuoteIdempotencyMapper;
import com.shipflow.quote.mapper.QuoteMapper;
import com.shipflow.quote.mapper.QuotePricingMapper;
import com.shipflow.quote.mapper.QuotePricingRuleRow;
import com.shipflow.store.domain.model.Store;
import com.shipflow.store.mapper.StoreMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuoteCreationApplicationServiceTest {
    private final QuoteMapper quotes = mock(QuoteMapper.class);
    private final QuoteIdempotencyMapper idempotency = mock(QuoteIdempotencyMapper.class);
    private final QuotePricingMapper pricing = mock(QuotePricingMapper.class);
    private final StoreMapper stores = mock(StoreMapper.class);
    private final QuoteAuditMapper audit = mock(QuoteAuditMapper.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-11T08:00:00Z"), ZoneOffset.UTC);
    private final QuoteQueryApplicationService query = new QuoteQueryApplicationService(quotes, new ObjectMapper(), clock);
    private final QuoteCreationApplicationService service = new QuoteCreationApplicationService(quotes, idempotency,
            pricing, stores, audit, query, new ObjectMapper(), clock);

    @Test
    void createsFrozenQuoteFromCurrentRuleAndWritesIdempotencyAndAudit() {
        CreateQuoteRequest request = request("US");
        setEligiblePrerequisites(request);
        when(quotes.findByQuoteNo(eq(7L), any())).thenReturn(createdQuote());

        var result = service.create(7L, 12L, request, "quote-key", "request-1");

        assertThat(result.id()).isEqualTo(99L);
        assertThat(result.destinationCountry()).isEqualTo("US");
        assertThat(result.declaredChargeableWeight()).isEqualByComparingTo("2.0");
        assertThat(result.amount()).isEqualByComparingTo("12.00");
        assertThat(result.validTo()).isEqualTo(Instant.parse("2026-08-11T08:30:00Z").atOffset(ZoneOffset.UTC));
        verify(quotes).insert(org.mockito.ArgumentMatchers.argThat(quote ->
                quote.priceRuleId().equals(5L) && quote.ruleVersionNo() == 2
                        && quote.feeDetail().contains("\"tierNo\":2") && quote.feeDetail().contains("\"destinationCountry\":\"US\"")));
        verify(idempotency).complete(7L, "createQuote", "quote-key", 99L);
        verify(audit).insert(7L, 12L, 99L, "request-1", LocalDateTime.of(2026, 8, 11, 8, 0));
    }

    @Test
    void rejectsInactiveOrUnsupportedChannelBeforeQuoteInsert() {
        CreateQuoteRequest request = request("JP");
        when(idempotency.find(7L, "createQuote", "quote-key")).thenReturn(null);
        when(stores.findById(7L, 3L)).thenReturn(activeStore());
        when(pricing.isActiveChannel(4L)).thenReturn(true);
        when(pricing.servesCountry(4L, "JP")).thenReturn(false);

        assertThatThrownBy(() -> service.create(7L, 12L, request, "quote-key", null))
                .isInstanceOf(QuoteException.class)
                .satisfies(error -> assertThat(((QuoteException) error).code()).isEqualTo("QUOTE-1006"));
        verify(quotes, never()).insert(any());
    }

    @Test
    void replaysCompletedIdempotencyKeyWithoutRevalidatingOrInserting() throws Exception {
        CreateQuoteRequest request = request("US");
        when(idempotency.find(7L, "createQuote", "quote-key"))
                .thenReturn(new QuoteIdempotencyMapper.Record(hash(request), 99L));
        when(quotes.findById(7L, 99L)).thenReturn(createdQuote());

        var replay = service.create(7L, 12L, request, "quote-key", null);

        assertThat(replay.id()).isEqualTo(99L);
        verify(quotes, never()).insert(any());
        verify(pricing, never()).isActiveChannel(any());
        verify(audit, never()).insert(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsSameIdempotencyKeyWithDifferentRequest() {
        when(idempotency.find(7L, "createQuote", "quote-key"))
                .thenReturn(new QuoteIdempotencyMapper.Record("different", 99L));

        assertThatThrownBy(() -> service.create(7L, 12L, request("US"), "quote-key", null))
                .isInstanceOf(QuoteException.class)
                .satisfies(error -> assertThat(((QuoteException) error).code()).isEqualTo("COMMON-1009"));
    }

    private void setEligiblePrerequisites(CreateQuoteRequest request) {
        when(idempotency.find(7L, "createQuote", "quote-key")).thenReturn(null);
        when(stores.findById(7L, 3L)).thenReturn(activeStore());
        when(pricing.isActiveChannel(4L)).thenReturn(true);
        when(pricing.servesCountry(4L, request.destinationCountry())).thenReturn(true);
        when(pricing.findCurrentRule(4L, LocalDateTime.of(2026, 8, 11, 8, 0))).thenReturn(ruleRow());
        when(pricing.findTiers(5L)).thenReturn(List.of(
                new PriceRuleTier(1, BigDecimal.ZERO, new BigDecimal("2"), PriceRuleTier.BillingMode.FIXED,
                        null, null, null, null, new BigDecimal("10")),
                new PriceRuleTier(2, new BigDecimal("2"), null, PriceRuleTier.BillingMode.FIRST_CONTINUE,
                        new BigDecimal("2"), new BigDecimal("12"), new BigDecimal("0.5"), new BigDecimal("3"), null)));
    }

    private CreateQuoteRequest request(String country) {
        return new CreateQuoteRequest(3L, 4L, new BigDecimal("1.2"), new BigDecimal("20"),
                new BigDecimal("20"), new BigDecimal("20"), country);
    }

    private QuotePricingRuleRow ruleRow() {
        return new QuotePricingRuleRow(5L, 4L, 2, "Air v2", "USD", new BigDecimal("5000"),
                PublishedPriceRule.RoundingMode.CEILING, new BigDecimal("0.5"), LocalDateTime.of(2026, 8, 1, 0, 0));
    }

    private Store activeStore() {
        return new Store(3L, 7L, "S1", "Store", "AMZ", "account", "ACTIVE", 0L, null, null);
    }

    private Quote createdQuote() {
        return new Quote(99L, 7L, "Q1", 3L, 4L, 5L, 2, new BigDecimal("1.2"), new BigDecimal("20"),
                new BigDecimal("20"), new BigDecimal("20"), new BigDecimal("1.600"), new BigDecimal("2.0"),
                new BigDecimal("12.00"), "USD", "{\"destinationCountry\":\"US\",\"tierNo\":2}",
                LocalDateTime.of(2026, 8, 11, 8, 0), LocalDateTime.of(2026, 8, 11, 8, 30), "VALID", 0L, null, null);
    }

    private String hash(CreateQuoteRequest request) throws Exception {
        String value = request.storeId() + "\n" + request.channelId() + "\n" + request.declaredWeight().toPlainString()
                + "\n" + request.declaredLength().toPlainString() + "\n" + request.declaredWidth().toPlainString()
                + "\n" + request.declaredHeight().toPlainString() + "\n" + request.destinationCountry();
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
