package com.shipflow.quote.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipflow.logistics.domain.model.PriceRuleTier;
import com.shipflow.quote.api.model.CreateQuoteRequest;
import com.shipflow.quote.api.model.QuoteResponse;
import com.shipflow.quote.domain.QuoteCalculator;
import com.shipflow.quote.domain.model.Quote;
import com.shipflow.quote.mapper.QuoteAuditMapper;
import com.shipflow.quote.mapper.QuoteIdempotencyMapper;
import com.shipflow.quote.mapper.QuoteMapper;
import com.shipflow.quote.mapper.QuotePricingMapper;
import com.shipflow.quote.mapper.QuotePricingRule;
import com.shipflow.quote.mapper.QuotePricingRuleRow;
import com.shipflow.store.domain.model.Store;
import com.shipflow.store.mapper.StoreMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Creates immutable tenant quote snapshots from the current published platform rule. */
@Service
public class QuoteCreationApplicationService {
    private static final String OPERATION = "createQuote";
    private static final DateTimeFormatter QUOTE_NUMBER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final QuoteMapper quoteMapper;
    private final QuoteIdempotencyMapper idempotencyMapper;
    private final QuotePricingMapper pricingMapper;
    private final StoreMapper storeMapper;
    private final QuoteAuditMapper auditMapper;
    private final QuoteQueryApplicationService queryService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public QuoteCreationApplicationService(QuoteMapper quoteMapper, QuoteIdempotencyMapper idempotencyMapper,
                                           QuotePricingMapper pricingMapper, StoreMapper storeMapper,
                                           QuoteAuditMapper auditMapper, QuoteQueryApplicationService queryService,
                                           ObjectMapper objectMapper, Clock clock) {
        this.quoteMapper = quoteMapper;
        this.idempotencyMapper = idempotencyMapper;
        this.pricingMapper = pricingMapper;
        this.storeMapper = storeMapper;
        this.auditMapper = auditMapper;
        this.queryService = queryService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public QuoteResponse create(Long tenantId, Long operatorUserId, CreateQuoteRequest request,
                                String idempotencyKey, String requestId) {
        requireTenant(tenantId);
        String country = request.destinationCountry().toUpperCase(Locale.ROOT);
        String requestHash = hash(request, country);
        QuoteIdempotencyMapper.Record prior = claimOrReplay(tenantId, idempotencyKey, requestHash);
        if (prior != null) {
            Quote existing = quoteMapper.findById(tenantId, prior.resourceId());
            if (existing == null) {
                throw new IllegalStateException("Completed quote idempotency record has no quote");
            }
            return queryService.responseOf(existing);
        }

        Store store = storeMapper.findById(tenantId, request.storeId());
        if (store == null) {
            throw new QuoteException("COMMON-1006", 404);
        }
        if (!"ACTIVE".equals(store.status())) {
            throw new QuoteException("QUOTE-1006", 422);
        }
        if (!pricingMapper.isActiveChannel(request.channelId())
                || !pricingMapper.servesCountry(request.channelId(), country)) {
            throw new QuoteException("QUOTE-1006", 422);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        QuotePricingRuleRow ruleRow = pricingMapper.findCurrentRule(request.channelId(), now);
        if (ruleRow == null) {
            throw new QuoteException("QUOTE-1002", 422);
        }
        QuotePricingRule rule = new QuotePricingRule(ruleRow.id(), ruleRow.channelId(), ruleRow.versionNo(),
                ruleRow.ruleName(), ruleRow.currency(), ruleRow.volumeDivisor(), ruleRow.roundingMode(),
                ruleRow.roundingIncrement(), ruleRow.effectiveFrom(), pricingMapper.findTiers(ruleRow.id()));
        QuoteCalculator.Calculation calculation;
        try {
            calculation = QuoteCalculator.calculate(rule.publishedRule(), request.declaredWeight(),
                    request.declaredLength(), request.declaredWidth(), request.declaredHeight());
        } catch (IllegalArgumentException exception) {
            throw new QuoteException("QUOTE-1001", 422);
        }

        String quoteNo = nextQuoteNo(now);
        LocalDateTime validTo = now.plusMinutes(30);
        Quote pending = new Quote(null, tenantId, quoteNo, request.storeId(), request.channelId(), rule.id(),
                rule.versionNo(), request.declaredWeight(), request.declaredLength(), request.declaredWidth(),
                request.declaredHeight(), calculation.volumeWeight(), calculation.chargeableWeight(),
                calculation.amount(), rule.currency(), feeDetail(request, country, rule, calculation), now, validTo,
                "VALID", 0L, null, null);
        quoteMapper.insert(pending);
        Quote created = quoteMapper.findByQuoteNo(tenantId, quoteNo);
        if (created == null) {
            throw new IllegalStateException("Quote was not created");
        }
        idempotencyMapper.complete(tenantId, OPERATION, idempotencyKey, created.id());
        auditMapper.insert(tenantId, operatorUserId, created.id(), requestId, now);
        return queryService.responseOf(created);
    }

    private QuoteIdempotencyMapper.Record claimOrReplay(Long tenantId, String key, String hash) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new QuoteException("COMMON-1001", 400);
        }
        QuoteIdempotencyMapper.Record record = idempotencyMapper.find(tenantId, OPERATION, key);
        if (record == null) {
            try {
                idempotencyMapper.insert(tenantId, OPERATION, key, hash, LocalDateTime.now(clock).plusMinutes(30));
            } catch (DuplicateKeyException exception) {
                record = idempotencyMapper.find(tenantId, OPERATION, key);
                if (record == null) {
                    throw exception;
                }
            }
        }
        if (record == null) {
            return null;
        }
        if (!hash.equals(record.requestHash())) {
            throw new QuoteException("COMMON-1009", 409);
        }
        if (record.resourceId() == null) {
            throw new QuoteException("COMMON-1010", 409);
        }
        return record;
    }

    private String feeDetail(CreateQuoteRequest request, String country, QuotePricingRule rule,
                             QuoteCalculator.Calculation calculation) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("destinationCountry", country);
        detail.put("declaredWeight", request.declaredWeight());
        detail.put("declaredLength", request.declaredLength());
        detail.put("declaredWidth", request.declaredWidth());
        detail.put("declaredHeight", request.declaredHeight());
        detail.put("volumeWeight", calculation.volumeWeight());
        detail.put("chargeableWeight", calculation.chargeableWeight());
        detail.put("volumeDivisor", rule.volumeDivisor());
        detail.put("roundingMode", rule.roundingMode().name());
        detail.put("roundingIncrement", rule.roundingIncrement());
        detail.put("priceRuleId", rule.id());
        detail.put("ruleVersionNo", rule.versionNo());
        detail.put("tierNo", calculation.tierNo());
        detail.put("amount", calculation.amount());
        detail.put("currency", rule.currency());
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize quote fee detail", exception);
        }
    }

    private String hash(CreateQuoteRequest request, String country) {
        return sha256(request.storeId() + "\n" + request.channelId() + "\n" + request.declaredWeight().toPlainString()
                + "\n" + request.declaredLength().toPlainString() + "\n" + request.declaredWidth().toPlainString()
                + "\n" + request.declaredHeight().toPlainString() + "\n" + country);
    }

    private String nextQuoteNo(LocalDateTime now) {
        return "Q" + QUOTE_NUMBER_TIME.format(now) + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void requireTenant(Long tenantId) {
        if (tenantId == null || tenantId < 1) {
            throw new QuoteException("COMMON-1004", 403);
        }
    }
}
