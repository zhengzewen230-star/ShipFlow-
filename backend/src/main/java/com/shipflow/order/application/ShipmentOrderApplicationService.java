package com.shipflow.order.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipflow.order.api.model.CreateShipmentOrderRequest;
import com.shipflow.order.api.model.ShipmentOrderResponse;
import com.shipflow.order.domain.model.ShipmentOrder;
import com.shipflow.order.mapper.ShipmentOrderIdempotencyMapper;
import com.shipflow.order.mapper.ShipmentOrderMapper;
import com.shipflow.logistics.application.LogisticsRejectionAuditService;
import com.shipflow.quote.domain.model.Quote;
import com.shipflow.quote.mapper.QuoteMapper;
import com.shipflow.quote.mapper.QuotePricingMapper;
import com.shipflow.store.mapper.StoreScopeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/** Atomically consumes one valid quote and creates its immutable order price snapshot. */
@Service
public class ShipmentOrderApplicationService {
    private static final String OPERATION = "createShipmentOrderFromQuote";
    private final ShipmentOrderMapper mapper; private final ShipmentOrderIdempotencyMapper idempotencyMapper;
    private final QuoteMapper quoteMapper; private final QuotePricingMapper pricingMapper; private final ObjectMapper objectMapper; private final Clock clock; private final LogisticsRejectionAuditService rejectionAudit;
    private final StoreScopeMapper storeScopeMapper;
    public ShipmentOrderApplicationService(ShipmentOrderMapper mapper, ShipmentOrderIdempotencyMapper idempotencyMapper, QuoteMapper quoteMapper, QuotePricingMapper pricingMapper, ObjectMapper objectMapper, Clock clock, StoreScopeMapper storeScopeMapper) {
        this(mapper, idempotencyMapper, quoteMapper, pricingMapper, objectMapper, clock, null, storeScopeMapper);
    }
    @Autowired
    public ShipmentOrderApplicationService(ShipmentOrderMapper mapper, ShipmentOrderIdempotencyMapper idempotencyMapper, QuoteMapper quoteMapper, QuotePricingMapper pricingMapper, ObjectMapper objectMapper, Clock clock, LogisticsRejectionAuditService rejectionAudit, StoreScopeMapper storeScopeMapper) {
        this.mapper=mapper; this.idempotencyMapper=idempotencyMapper; this.quoteMapper=quoteMapper; this.pricingMapper=pricingMapper; this.objectMapper=objectMapper; this.clock=clock; this.rejectionAudit=rejectionAudit; this.storeScopeMapper=storeScopeMapper;
    }
    @Transactional
    public ShipmentOrderResponse create(Long tenantId, Long operatorUserId, Long quoteId, CreateShipmentOrderRequest request, String key, String requestId) {
        if (tenantId == null || tenantId < 1) throw new ShipmentOrderException("COMMON-1004",403);
        String hash = hash(quoteId, request);
        ShipmentOrderIdempotencyMapper.Record prior = claim(tenantId,key,hash);
        if (prior != null) return response(requireOrder(mapper.findByIdempotencyKey(tenantId,key)));
        Quote quote=quoteMapper.findById(tenantId,quoteId);
        if (quote==null) throw new ShipmentOrderException("COMMON-1006",404);
        if (!storeScopeMapper.canAccessStore(tenantId, operatorUserId, quote.storeId())) throw new ShipmentOrderException("COMMON-1006",404);
        LocalDateTime now=LocalDateTime.now(clock);
        if (!"VALID".equals(quote.status())) throw new ShipmentOrderException("ORDER-1003",422);
        if (!now.isBefore(quote.validTo())) throw new ShipmentOrderException("QUOTE-1003",422);
        if (!quoteMapper.isStoreActive(tenantId, quote.storeId())) throw new ShipmentOrderException("STORE-1002",422);
        if (!pricingMapper.isActiveChannel(quote.channelId())) {
            if (rejectionAudit != null) {
                rejectionAudit.record(tenantId, operatorUserId, "CREATE_ORDER_REJECTED", "LOGISTICS_CHANNEL",
                        quote.channelId(), requestId, "CHANNEL_INACTIVE");
            }
            throw new ShipmentOrderException("CHANNEL-1001",422);
        }
        if (mapper.findByQuoteId(tenantId,quoteId)!=null) throw new ShipmentOrderException("QUOTE-1004",409);
        QuoteSnapshot snapshot=readSnapshot(quote);
        String destination=snapshot.destinationCountry();
        if (!destination.equalsIgnoreCase(request.receiverAddress().countryCode())) throw new ShipmentOrderException("ORDER-1003",422);
        ShipmentOrder pending=new ShipmentOrder(null,tenantId,next("SO",now),key,quote.storeId(),quote.id(),quote.channelId(),"DRAFT",
                request.senderAddress().countryCode().toUpperCase(Locale.ROOT),destination.toUpperCase(Locale.ROOT),quote.declaredWeight(),quote.declaredLength(),quote.declaredWidth(),quote.declaredHeight(),quote.volumeWeight(),quote.chargeableWeight(),quote.amount(),quote.currency(),0L,now);
        try { mapper.insertOrder(pending); } catch (DuplicateKeyException e) { return resolveDuplicate(tenantId,quoteId,key); }
        ShipmentOrder created=requireOrder(mapper.findByIdempotencyKey(tenantId,key));
        mapper.insertSnapshot(tenantId,created.id(),quote,snapshot.volumeDivisor(),snapshot.roundingMode(),snapshot.roundingIncrement());
        mapper.insertAddress(tenantId,created.id(),"SENDER",request.senderAddress()); mapper.insertAddress(tenantId,created.id(),"RECEIVER",request.receiverAddress());
        mapper.insertPackage(tenantId,created.id(),next("PK",now),created); Long packageId=mapper.findPackageId(tenantId,created.id());
        if(packageId==null) throw new IllegalStateException("Package was not created"); int no=1; for(var item:request.items()) mapper.insertItem(tenantId,packageId,no++,item);
        mapper.insertAudit(tenantId,operatorUserId,created.id(),requestId,now); idempotencyMapper.complete(tenantId,OPERATION,key,created.id()); return response(created);
    }
    private ShipmentOrderIdempotencyMapper.Record claim(Long tenant,String key,String hash){ if(key==null||key.isBlank()||key.length()>128) throw new ShipmentOrderException("COMMON-1001",400); var r=idempotencyMapper.find(tenant,OPERATION,key); if(r==null) try{idempotencyMapper.insert(tenant,OPERATION,key,hash,LocalDateTime.now(clock).plusMinutes(30));}catch(DuplicateKeyException e){r=idempotencyMapper.find(tenant,OPERATION,key);if(r==null)throw e;} if(r==null)return null; if(!hash.equals(r.requestHash()))throw new ShipmentOrderException("COMMON-1009",409); if(r.resourceId()==null)throw new ShipmentOrderException("COMMON-1010",409); return r; }
    private ShipmentOrderResponse resolveDuplicate(Long tenant,Long quoteId,String key){ ShipmentOrder existing=mapper.findByQuoteId(tenant,quoteId); if(existing!=null&&key.equals(existing.idempotencyKey())) return response(existing); throw new ShipmentOrderException("QUOTE-1004",409); }
    private QuoteSnapshot readSnapshot(Quote quote){
        try {
            JsonNode fee=objectMapper.readTree(quote.feeDetail());
            String destination=fee.required("destinationCountry").asText().trim().toUpperCase(Locale.ROOT);
            BigDecimal volumeDivisor=fee.required("volumeDivisor").decimalValue();
            String roundingMode=fee.required("roundingMode").asText().trim();
            BigDecimal roundingIncrement=fee.required("roundingIncrement").decimalValue();
            if(!destination.matches("[A-Z]{2}") || volumeDivisor.signum()<=0 || roundingMode.isEmpty() || roundingIncrement.signum()<=0) throw new IllegalArgumentException();
            return new QuoteSnapshot(destination,volumeDivisor,roundingMode,roundingIncrement);
        } catch (Exception exception) {
            throw new ShipmentOrderException("ORDER-1003",422);
        }
    }
    private ShipmentOrder requireOrder(ShipmentOrder o){if(o==null)throw new IllegalStateException("Order was not created");return o;}
    private ShipmentOrderResponse response(ShipmentOrder o){return new ShipmentOrderResponse(o.id(),o.orderNo(),o.quoteId(),o.currentStatus(),o.estimatedFee(),o.currency(),o.chargeableWeight(),o.version(),o.createdAt().atOffset(ZoneOffset.UTC));}
    private String next(String prefix,LocalDateTime now){return prefix+DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now)+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);}
    private String hash(Long id,CreateShipmentOrderRequest r){return sha256(id+"\n"+r.toString());} private String sha256(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private record QuoteSnapshot(String destinationCountry, BigDecimal volumeDivisor, String roundingMode, BigDecimal roundingIncrement) { }
}
