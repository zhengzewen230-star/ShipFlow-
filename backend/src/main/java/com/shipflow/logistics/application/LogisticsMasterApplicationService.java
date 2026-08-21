package com.shipflow.logistics.application;

import com.shipflow.logistics.api.model.*;
import com.shipflow.logistics.domain.model.*;
import com.shipflow.logistics.mapper.LogisticsChannelRow;
import com.shipflow.logistics.mapper.LogisticsMasterMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.time.Clock;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.shipflow.logistics.domain.PriceRuleTierValidator;
import com.shipflow.logistics.mapper.LogisticsIdempotencyMapper;
import com.shipflow.logistics.mapper.LogisticsAuditMapper;
import com.shipflow.logistics.mapper.PriceRuleRow;
import com.shipflow.logistics.mapper.PublicLogisticsChannelRow;

/** Platform-only orchestration for logistics master data. */
@Service
public class LogisticsMasterApplicationService {
    private final LogisticsMasterMapper mapper;
    private final LogisticsIdempotencyMapper idempotency; private final LogisticsAuditMapper audit; private final Clock clock;
    public LogisticsMasterApplicationService(LogisticsMasterMapper mapper, LogisticsIdempotencyMapper idempotency, LogisticsAuditMapper audit, Clock clock) { this.mapper = mapper; this.idempotency=idempotency; this.audit=audit; this.clock=clock; }

    public LogisticsProviderPage providers(String status, int page, int pageSize) {
        checkPage(page, pageSize); long total = mapper.countProviders(status);
        return new LogisticsProviderPage(page, pageSize, total, pages(total, pageSize), mapper.pageProviders(status, offset(page, pageSize), pageSize));
    }
    public LogisticsProvider provider(Long id) { return providerRequired(id); }
    @Transactional public LogisticsProvider createProvider(CreateLogisticsProviderRequest request, String key, Long operator, String requestId) {
        String hash=hash(request.providerCode()+"\n"+request.providerName()); LogisticsIdempotencyMapper.Record prior=prior("createLogisticsProvider",key,hash); if(prior!=null)return providerRequired(prior.resourceId());
        if (mapper.findProviderByCode(request.providerCode()) != null) throw duplicate();
        try { mapper.insertProvider(new LogisticsProvider(null, request.providerCode(), request.providerName(), "ACTIVE", 0, null, null)); }
        catch (DuplicateKeyException exception) { throw duplicate(); }
        LogisticsProvider created=providerRequired(mapper.findProviderByCode(request.providerCode()).id()); complete("createLogisticsProvider",key,created.id()); audit.insert(operator,"CREATE","LOGISTICS_PROVIDER",created.id(),requestId,LocalDateTime.now(clock)); return created;
    }
    @Transactional public LogisticsProvider updateProvider(Long id, UpdateLogisticsProviderRequest request, Long operator, String requestId) {
        if (!activeStatus(request.status())) throw rule("LOGISTICS-1002");
        if (mapper.updateProvider(id, request.providerName(), request.status(), request.version()) != 1) throw conflictOrMissingProvider(id);
        LogisticsProvider result=providerRequired(id); audit.insert(operator,"UPDATE","LOGISTICS_PROVIDER",id,requestId,LocalDateTime.now(clock)); return result;
    }
    public LogisticsChannelPage channels(Long providerId, String status, int page, int pageSize) {
        checkPage(page, pageSize); long total = mapper.countChannels(providerId, status);
        return new LogisticsChannelPage(page, pageSize, total, pages(total, pageSize), mapper.pageChannels(providerId, status, offset(page, pageSize), pageSize).stream().map(this::toChannel).toList());
    }
    public LogisticsChannel channel(Long id) { return channelRequired(id); }
    public List<PublishedPriceRule> publishedPriceRules(Long channelId) {
        channelRequired(channelId);
        return mapper.findPublishedPriceRules(channelId).stream().map(this::toPublishedRule).toList();
    }
    public PublishedPriceRule publishedPriceRule(Long channelId, Long priceRuleId) {
        channelRequired(channelId);
        PublishedPriceRule rule = publishedRuleRequired(priceRuleId);
        if (!rule.channelId().equals(channelId)) throw new LogisticsException("COMMON-1006", 404);
        return rule;
    }
    public LogisticsChannelPage availableChannels(String countryCode, int page, int pageSize) {
        checkPage(page, pageSize);
        String country = countryCode == null ? null : countryCode.toUpperCase(Locale.ROOT);
        long total = mapper.countAvailableChannels(country);
        return new LogisticsChannelPage(page, pageSize, total, pages(total, pageSize), mapper.pageAvailableChannels(country, offset(page, pageSize), pageSize).stream().map(this::toChannel).toList());
    }
    public PublicLogisticsChannelPage publicChannels(String channelCode, String channelName, String serviceCountry,
                                                     String status, int page, int pageSize, String sortField,
                                                     String sortDirection) {
        checkPage(page, pageSize);
        String normalizedStatus = normalizeStatus(status);
        String normalizedSortField = normalizeSortField(sortField);
        String normalizedSortDirection = normalizeSortDirection(sortDirection);
        String country = normalizeCountry(serviceCountry);
        String code = normalizeFilter(channelCode, 64);
        String name = normalizeFilter(channelName, 128);
        long total = mapper.countPublicChannels(code, name, country, normalizedStatus);
        return new PublicLogisticsChannelPage(page, pageSize, total, pages(total, pageSize),
                mapper.pagePublicChannels(code, name, country, normalizedStatus, normalizedSortField,
                        normalizedSortDirection, offset(page, pageSize), pageSize).stream().map(this::toPublicChannel).toList());
    }
    public PublicLogisticsChannel publicChannel(Long channelId) {
        PublicLogisticsChannelRow row = mapper.findPublicChannel(channelId);
        if (row == null) throw new LogisticsException("COMMON-1006", 404);
        return toPublicChannel(row);
    }
    public LogisticsChannel availableChannel(Long channelId) {
        LogisticsChannelRow row = mapper.findAvailableChannel(channelId);
        if (row == null) throw new LogisticsException("COMMON-1006", 404);
        return toChannel(row);
    }
    @Transactional public LogisticsChannel createChannel(CreateLogisticsChannelRequest request, String key, Long operator, String requestId) {
        String hash=hash(request.providerId()+"\n"+request.channelCode()+"\n"+request.channelName()+"\n"+request.transportMode()+"\n"+request.serviceArea()); LogisticsIdempotencyMapper.Record prior=prior("createLogisticsChannel",key,hash); if(prior!=null)return channelRequired(prior.resourceId());
        providerRequired(request.providerId());
        if (mapper.findChannelByCode(request.providerId(), request.channelCode()) != null) throw duplicate();
        try { mapper.insertChannel(new LogisticsChannel(null, request.providerId(), request.channelCode(), request.channelName(), request.transportMode(), request.serviceArea(), "DISABLED", 0, List.of(), null, null)); }
        catch (DuplicateKeyException exception) { throw duplicate(); }
        LogisticsChannelRow row = mapper.findChannelByCode(request.providerId(), request.channelCode());
        if (row == null) throw new IllegalStateException("channel was not created");
        LogisticsChannel created=toChannel(row); complete("createLogisticsChannel",key,created.id()); audit.insert(operator,"CREATE","LOGISTICS_CHANNEL",created.id(),requestId,LocalDateTime.now(clock)); return created;
    }
    @Transactional public LogisticsChannel updateChannel(Long id, UpdateLogisticsChannelRequest request, Long operator, String requestId) {
        LogisticsChannel existing = channelRequired(id);
        if (!activeStatus(request.status())) throw rule("LOGISTICS-1002");
        if ("ACTIVE".equals(request.status()) && (!"ACTIVE".equals(providerRequired(existing.providerId()).status()) || existing.serviceCountries().isEmpty() || !mapper.hasPublishedPriceRule(id))) throw rule("LOGISTICS-1003");
        if (mapper.updateChannel(id, request.channelName(), request.transportMode().name(), request.serviceArea(), request.status(), request.version()) != 1) throw conflictOrMissingChannel(id);
        LogisticsChannel result=channelRequired(id); audit.insert(operator,"UPDATE","LOGISTICS_CHANNEL",id,requestId,LocalDateTime.now(clock)); return result;
    }
    @Transactional public LogisticsChannel replaceServiceCountries(Long id, ServiceCountriesRequest request, Long operator, String requestId) {
        LogisticsChannel channel = channelRequired(id);
        if (channel.version() != request.version()) throw new LogisticsException("COMMON-1005", 409);
        List<String> countries = request.countryCodes().stream().map(value -> value.toUpperCase(Locale.ROOT)).distinct().toList();
        if (countries.size() != request.countryCodes().size()) throw new LogisticsException("COMMON-1001", 400);
        mapper.deleteServiceCountries(id);
        countries.forEach(country -> mapper.insertServiceCountry(id, country));
        if (mapper.updateChannel(id, channel.channelName(), channel.transportMode().name(), channel.serviceArea(), channel.status(), channel.version()) != 1) throw conflictOrMissingChannel(id);
        LogisticsChannel result=channelRequired(id); audit.insert(operator,"REPLACE_SERVICE_COUNTRIES","LOGISTICS_CHANNEL",id,requestId,LocalDateTime.now(clock)); return result;
    }
    @Transactional public PublishedPriceRule publishPriceRule(Long channelId, PublishPriceRuleRequest request, String key, Long operator, String requestId) {
        String hash=hash(channelId+"\n"+request.versionNo()+"\n"+request.ruleName()+"\n"+request.tiers()); LogisticsIdempotencyMapper.Record prior=prior("publishPriceRule",key,hash);
        if(prior!=null) return publishedRuleRequired(prior.resourceId());
        channelRequired(channelId);
        List<PriceRuleTier> tiers = request.tiers().stream().map(t -> new PriceRuleTier(t.tierNo(), t.minWeight(), t.maxWeight(), t.billingMode(), t.firstWeight(), t.firstFee(), t.additionalWeight(), t.additionalFee(), t.tierFee())).toList();
        try { PriceRuleTierValidator.validate(tiers); }
        catch (IllegalArgumentException exception) { throw rule("LOGISTICS-1004"); }
        try { mapper.insertPublishedPriceRule(channelId, request.versionNo(), request.ruleName(), request.currency(), request.volumeDivisor(), request.roundingMode().name(), request.roundingIncrement(), request.effectiveFrom()); }
        catch (DuplicateKeyException exception) { throw new LogisticsException("LOGISTICS-1005", 409); }
        Long id = mapper.findPriceRuleId(channelId, request.versionNo());
        if (id == null) throw new IllegalStateException("price rule was not created");
        tiers.forEach(tier -> mapper.insertPriceRuleTier(id, tier));
        PublishedPriceRule result=publishedRuleRequired(id); complete("publishPriceRule",key,id); audit.insert(operator,"PUBLISH","PRICE_RULE",id,requestId,LocalDateTime.now(clock)); return result;
    }
    private LogisticsProvider providerRequired(Long id) { LogisticsProvider value = mapper.findProvider(id); if(value == null) throw new LogisticsException("COMMON-1006", 404); return value; }
    private LogisticsChannel channelRequired(Long id) { LogisticsChannelRow row = mapper.findChannel(id); if(row == null) throw new LogisticsException("COMMON-1006", 404); return toChannel(row); }
    private LogisticsChannel toChannel(LogisticsChannelRow row) { return new LogisticsChannel(row.id(), row.providerId(), row.channelCode(), row.channelName(), row.transportMode(), row.serviceArea(), row.status(), row.version(), mapper.findServiceCountries(row.id()), row.createdAt(), row.updatedAt()); }
    private PublicLogisticsChannel toPublicChannel(PublicLogisticsChannelRow row) {
        return new PublicLogisticsChannel(row.id(), row.providerName(), row.channelCode(), row.channelName(),
                row.transportMode(), mapper.findServiceCountries(row.id()), row.priceRuleVersion(),
                row.effectiveFrom(), row.effectiveTo(), row.volumeDivisor(), row.status(),
                List.of("supportedCargoAttributes", "publicPriceDescription", "priceRuleTiers", "internalCost", "supplierConfiguration"));
    }
    private PublishedPriceRule publishedRuleRequired(Long id) { PriceRuleRow row=mapper.findPublishedPriceRule(id); if(row==null)throw new LogisticsException("COMMON-1006",404); return toPublishedRule(row); }
    private PublishedPriceRule toPublishedRule(PriceRuleRow row) { return new PublishedPriceRule(row.id(),row.channelId(),row.versionNo(),row.ruleName(),row.currency(),row.volumeDivisor(),row.roundingMode(),row.roundingIncrement(),row.effectiveFrom(),mapper.findPriceRuleTiers(row.id())); }
    private LogisticsException conflictOrMissingProvider(Long id) { return mapper.findProvider(id) == null ? new LogisticsException("COMMON-1006", 404) : new LogisticsException("COMMON-1005", 409); }
    private LogisticsException conflictOrMissingChannel(Long id) { return mapper.findChannel(id) == null ? new LogisticsException("COMMON-1006", 404) : new LogisticsException("COMMON-1005", 409); }
    private LogisticsException duplicate() { return new LogisticsException("LOGISTICS-1001", 409); }
    private LogisticsException rule(String code) { return new LogisticsException(code, 422); }
    private boolean activeStatus(String status) { return "ACTIVE".equals(status) || "DISABLED".equals(status); }
    private String normalizeStatus(String status) { if (status == null || status.isBlank()) return null; if (!activeStatus(status)) throw new LogisticsException("COMMON-1001", 400); return status; }
    private String normalizeSortField(String value) { if (value == null || value.isBlank()) return "updatedAt"; return switch (value) { case "channelCode", "channelName", "providerName", "status", "effectiveFrom", "updatedAt" -> value; default -> throw new LogisticsException("COMMON-1001", 400); }; }
    private String normalizeSortDirection(String value) { if (value == null || value.isBlank()) return "DESC"; if ("ASC".equals(value) || "DESC".equals(value)) return value; throw new LogisticsException("COMMON-1001", 400); }
    private String normalizeCountry(String value) { if (value == null || value.isBlank()) return null; String country = value.trim().toUpperCase(Locale.ROOT); if (!country.matches("[A-Z]{2}")) throw new LogisticsException("COMMON-1001", 400); return country; }
    private String normalizeFilter(String value, int max) { if (value == null || value.isBlank()) return null; String result = value.trim(); if (result.length() > max) throw new LogisticsException("COMMON-1001", 400); return result; }
    private void checkPage(int page, int pageSize) { if(page < 1 || pageSize < 1 || pageSize > 100) throw new LogisticsException("COMMON-1001", 400); }
    private int offset(int page, int pageSize) { return (page - 1) * pageSize; }
    private int pages(long total, int pageSize) { return (int) ((total + pageSize - 1) / pageSize); }
    private LogisticsIdempotencyMapper.Record prior(String operation,String key,String hash){if(key==null||key.isBlank()||key.length()>128)throw new LogisticsException("COMMON-1001",400);var record=idempotency.find(operation,key);if(record==null){try{idempotency.insert(operation,key,hash,LocalDateTime.now(clock).plusMinutes(30));}catch(DuplicateKeyException e){record=idempotency.find(operation,key);if(record==null)throw e;}}if(record!=null){if(!hash.equals(record.requestHash()))throw new LogisticsException("COMMON-1009",409);if(record.resourceId()==null)throw new LogisticsException("COMMON-1010",409);}return record;}
    private void complete(String operation,String key,Long resourceId){idempotency.complete(operation,key,resourceId);}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception exception){throw new IllegalStateException(exception);}}
}
