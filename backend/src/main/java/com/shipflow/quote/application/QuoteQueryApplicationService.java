package com.shipflow.quote.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipflow.quote.api.model.QuotePageResponse;
import com.shipflow.quote.api.model.QuoteResponse;
import com.shipflow.quote.api.model.QuoteValidationResponse;
import com.shipflow.quote.domain.model.Quote;
import com.shipflow.quote.mapper.QuoteMapper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

/** Read-only orchestration for tenant-scoped quote history and order eligibility. */
@Service
public class QuoteQueryApplicationService {
    private static final Set<String> STATUSES = Set.of("VALID", "EXPIRED", "CANCELLED");
    private static final TypeReference<Map<String, Object>> FEE_DETAIL_TYPE = new TypeReference<>() { };

    private final QuoteMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public QuoteQueryApplicationService(QuoteMapper mapper, ObjectMapper objectMapper, Clock clock) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public QuotePageResponse list(Long tenantId, Long userId, String quoteNo, Long storeId, Long channelId,
                                  String destinationCountry, String status, LocalDateTime createdFrom, LocalDateTime createdTo,
                                  LocalDateTime validFrom, LocalDateTime validTo, String sortField, String sortDirection,
                                  int page, int pageSize) {
        requireTenant(tenantId);
        if (userId == null || userId < 1 || page < 1 || pageSize < 1 || pageSize > 100 || (status != null && !STATUSES.contains(status))
                || !Set.of("createdAt", "quoteNo", "validTo", "amount", "status").contains(sortField == null ? "createdAt" : sortField)
                || !("ASC".equals(sortDirection) || "DESC".equals(sortDirection) || sortDirection == null)
                || (destinationCountry != null && !destinationCountry.matches("[A-Z]{2}"))) {
            throw new QuoteException("COMMON-1001", 400);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        long total = mapper.countForUser(tenantId, userId, quoteNo, storeId, channelId, destinationCountry, status, createdFrom, createdTo, validFrom, validTo, now);
        var items = mapper.findPageForUser(tenantId, userId, quoteNo, storeId, channelId, destinationCountry, status, createdFrom, createdTo, validFrom, validTo,
                now, sortField == null ? "createdAt" : sortField, sortDirection == null ? "DESC" : sortDirection, (page - 1) * pageSize, pageSize).stream().map(this::response).toList();
        return new QuotePageResponse(page, pageSize, total,
                (int) ((total + pageSize - 1) / pageSize), items);
    }


    public QuoteResponse get(Long tenantId, Long userId, Long quoteId) {
        return response(required(tenantId, userId, quoteId));
    }

    public QuoteValidationResponse validate(Long tenantId, Long userId, Long quoteId) {
        Quote quote = required(tenantId, userId, quoteId);
        boolean expired = "EXPIRED".equals(quote.status())
                || !quote.validTo().isAfter(LocalDateTime.now(clock));
        if ("CANCELLED".equals(quote.status())) {
            return new QuoteValidationResponse(quote.id(), true, expired, false, "CANCELLED");
        }
        if (expired) {
            return new QuoteValidationResponse(quote.id(), true, true, false, "EXPIRED");
        }
        if (mapper.hasShipmentOrder(tenantId, quoteId)) {
            return new QuoteValidationResponse(quote.id(), true, false, false, "ALREADY_USED");
        }
        return new QuoteValidationResponse(quote.id(), true, false, true, "AVAILABLE");
    }

    private Quote required(Long tenantId, Long userId, Long quoteId) {
        requireTenant(tenantId);
        if (quoteId == null || quoteId < 1) {
            throw new QuoteException("COMMON-1006", 404);
        }
        Quote quote = mapper.findByIdForUser(tenantId, userId, quoteId);
        if (quote == null) {
            throw new QuoteException("COMMON-1006", 404);
        }
        return quote;
    }

    private void requireTenant(Long tenantId) {
        if (tenantId == null || tenantId < 1) {
            throw new QuoteException("COMMON-1004", 403);
        }
    }

    public QuoteResponse responseOf(Quote quote) {
        Map<String, Object> feeDetail;
        try {
            feeDetail = objectMapper.readValue(quote.feeDetail(), FEE_DETAIL_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid stored quote fee detail", exception);
        }
        Object destination = feeDetail.getOrDefault("destinationCountry", feeDetail.get("destination_country"));
        return new QuoteResponse(quote.id(), quote.quoteNo(), quote.storeId(), quote.channelId(),
                destination == null ? null : destination.toString(), quote.ruleVersionNo(),
                quote.declaredWeight(), quote.declaredLength(), quote.declaredWidth(), quote.declaredHeight(),
                quote.volumeWeight(), quote.chargeableWeight(), quote.amount(), quote.currency(), feeDetail,
                quote.validFrom().atOffset(ZoneOffset.UTC), quote.validTo().atOffset(ZoneOffset.UTC),
                effectiveStatus(quote), quote.version());
    }

    private String effectiveStatus(Quote quote) {
        if ("VALID".equals(quote.status()) && !quote.validTo().isAfter(LocalDateTime.now(clock))) {
            return "EXPIRED";
        }
        return quote.status();
    }

    private QuoteResponse response(Quote quote) {
        return responseOf(quote);
    }
}
