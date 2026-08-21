package com.shipflow.store.application;

import com.shipflow.store.api.model.*;
import com.shipflow.store.domain.model.*;
import com.shipflow.store.mapper.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class StoreApplicationService {
    private final StoreMapper mapper;
    private final StoreIdempotencyMapper idem;
    private final StoreAuditMapper audit;
    private final StoreResourceMapper resources;
    private final StoreScopeMapper scope;
    private final Clock clock;

    public StoreApplicationService(StoreMapper mapper, StoreIdempotencyMapper idem,
                                   StoreAuditMapper audit, Clock clock) {
        this(mapper, idem, audit, null, null, clock);
    }

    @Autowired
    public StoreApplicationService(StoreMapper mapper, StoreIdempotencyMapper idem,
                                   StoreAuditMapper audit, StoreResourceMapper resources,
                                   StoreScopeMapper scope, Clock clock) {
        this.mapper = mapper;
        this.idem = idem;
        this.audit = audit;
        this.resources = resources;
        this.scope = scope;
        this.clock = clock;
    }

    @Transactional
    public Store create(Long tenantId, CreateStoreRequest request, String key, Long operatorId, String requestId) {
        checkKey(key);
        String hash = hash(request.storeCode() + "\n" + request.storeName() + "\n" + request.platformCode() + "\n" + request.platformAccount());
        var prior = idem.find(tenantId, "createStore", key);
        if (prior != null) {
            if (!hash.equals(prior.requestHash())) throw new StoreException("COMMON-1009", 409);
            if (prior.resourceId() != null) return required(tenantId, prior.resourceId());
            throw new StoreException("COMMON-1010", 409);
        }
        try {
            idem.insert(tenantId, "createStore", key, hash, "POST", "/api/v1/stores", now().plusMinutes(30));
            if (mapper.findByCode(tenantId, request.storeCode()) != null || mapper.findByAccount(tenantId, request.platformCode(), request.platformAccount()) != null) {
                throw new StoreException("STORE-1001", 409);
            }
            mapper.insert(new Store(null, tenantId, request.storeCode(), request.storeName(), request.platformCode(), request.platformAccount(), "ACTIVE", 0, null, null));
            Store store = mapper.findByCode(tenantId, request.storeCode());
            if (store == null) throw new IllegalStateException("Store was not created");
            idem.complete(tenantId, "createStore", key, store.id());
            audit.insert(tenantId, operatorId, "CREATE", store.id(), requestId, now());
            return store;
        } catch (DuplicateKeyException exception) {
            throw new StoreException("STORE-1001", 409);
        }
    }

    public StorePage page(Long tenantId, Long userId, String storeCode, String storeName, String platform,
                          String status, String sortBy, String sortDirection, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new StoreException("COMMON-1001", 400);
        String code = optional(storeCode, 64), name = optional(storeName, 128), platformCode = optional(platform, 64);
        String normalizedStatus = optional(status, 16);
        if (normalizedStatus != null && !Set.of("ACTIVE", "DISABLED").contains(normalizedStatus)) throw new StoreException("COMMON-1001", 400);
        String normalizedSort = sortBy == null || sortBy.isBlank() ? "updatedAt" : sortBy.trim();
        if (!Set.of("storeCode", "storeName", "platformCode", "status", "updatedAt").contains(normalizedSort)) throw new StoreException("COMMON-1001", 400);
        String direction = sortDirection == null || sortDirection.isBlank() ? "DESC" : sortDirection.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ASC", "DESC").contains(direction)) throw new StoreException("COMMON-1001", 400);
        long total = mapper.countForUser(tenantId, userId, code, name, platformCode, normalizedStatus, normalizedSort, direction);
        return new StorePage(page, pageSize, total, (int) ((total + pageSize - 1) / pageSize),
                mapper.pageForUser(tenantId, userId, code, name, platformCode, normalizedStatus, normalizedSort, direction, (page - 1) * pageSize, pageSize));
    }

    public Store get(Long tenantId, Long userId, Long storeId) { return requiredForUser(tenantId, userId, storeId); }

    public StoreDetailResponse getDetail(Long tenantId, Long userId, Long storeId) {
        Store store = requiredForUser(tenantId, userId, storeId);
        StoreAddress address = resources == null ? null : resources.findDefaultAddress(tenantId, storeId);
        StoreChannel channel = resources == null ? null : resources.findDefaultChannel(tenantId, storeId);
        String addressSummary = address == null ? null : address.countryCode() + " / " + address.city() + " / " + address.addressLine1();
        String channelSummary = channel == null ? null : channel.channelCode() + " / " + channel.channelName();
        List<String> unavailable = new java.util.ArrayList<>();
        if (resources == null) {
            unavailable.add("countryRegion");
            unavailable.add("defaultShippingAddress");
            unavailable.add("defaultLogisticsChannel");
        }
        long configurationVersion = resources == null ? 0 : resources.configurationVersion(tenantId, storeId);
        return new StoreDetailResponse(store.id(), store.tenantId(), store.storeCode(), store.storeName(), store.platformCode(), mask(store.platformAccount()),
                address == null ? null : address.countryCode(), addressSummary, channelSummary, store.status(), store.version(), store.createdAt(), store.updatedAt(),
                mapper.countHistoricalOrders(tenantId, storeId), audit.findRecent(tenantId, storeId, 10), unavailable, configurationVersion);
    }

    public StoreAddressResponse defaultAddress(Long tenantId, Long userId, Long storeId) {
        requiredForUser(tenantId, userId, storeId);
        StoreAddress address = requireResources().findDefaultAddress(tenantId, storeId);
        return address == null ? null : addressResponse(address);
    }

    public List<StoreChannelResponse> channels(Long tenantId, Long userId, Long storeId) {
        requiredForUser(tenantId, userId, storeId);
        return requireResources().findChannels(tenantId, storeId).stream().map(this::channelResponse).toList();
    }

    @Transactional
    public StoreAddressResponse updateDefaultAddress(Long tenantId, Long userId, Long storeId,
                                                     UpdateStoreAddressRequest request, String key, String requestId) {
        requireManage(tenantId, userId, storeId);
        checkKey(key);
        String hash = hash(storeId + "\n" + request.toString());
        var prior = idem.find(tenantId, "updateStoreAddress", key);
        if (prior != null) {
            if (!hash.equals(prior.requestHash())) throw new StoreException("COMMON-1009", 409);
            if (prior.resourceId() != null) return defaultAddress(tenantId, userId, storeId);
            throw new StoreException("COMMON-1010", 409);
        }
        StoreResourceMapper resourceMapper = requireResources();
        try {
            idem.insert(tenantId, "updateStoreAddress", key, hash, "PUT", "/api/v1/stores/" + storeId + "/default-address", now().plusMinutes(30));
            StoreAddress current = resourceMapper.findAddressByCode(tenantId, storeId, request.addressCode());
            StoreAddress value = new StoreAddress(current == null ? null : current.id(), tenantId, storeId, request.addressCode(), request.contactName(), request.companyName(),
                    request.phone(), request.email(), request.countryCode(), request.stateProvince(), request.city(), request.district(), request.addressLine1(),
                    request.addressLine2(), request.postalCode(), "ACTIVE", true, request.version(), userId, userId, current == null ? null : current.createdAt(), now());
            resourceMapper.clearOtherDefaultAddresses(tenantId, storeId, current == null ? -1L : current.id());
            if (current == null) resourceMapper.insertAddress(value); else if (resourceMapper.updateAddress(value) != 1) throw new StoreException("COMMON-1005", 409);
            StoreAddress saved = resourceMapper.findAddressByCode(tenantId, storeId, request.addressCode());
            if (saved == null) throw new StoreException("COMMON-1005", 409);
            resourceMapper.clearOtherDefaultAddresses(tenantId, storeId, saved.id());
            idem.complete(tenantId, "updateStoreAddress", key, saved.id());
            audit.insert(tenantId, userId, "STORE_ADDRESS_UPDATE", storeId, requestId, now());
            return defaultAddress(tenantId, userId, storeId);
        } catch (DuplicateKeyException exception) {
            throw new StoreException("COMMON-1005", 409);
        }
    }

    @Transactional
    public List<StoreChannelResponse> setDefaultChannel(Long tenantId, Long userId, Long storeId,
                                                        SetDefaultStoreChannelRequest request, String key, String requestId) {
        requireManage(tenantId, userId, storeId);
        checkKey(key);
        StoreResourceMapper resourceMapper = requireResources();
        StoreChannel publicChannel = resourceMapper.findPublicActiveChannel(request.channelId());
        if (publicChannel == null) throw new StoreException("STORE-1004", 422);
        String hash = hash(storeId + "\n" + request.toString());
        var prior = idem.find(tenantId, "setDefaultStoreChannel", key);
        if (prior != null) {
            if (!hash.equals(prior.requestHash())) throw new StoreException("COMMON-1009", 409);
            if (prior.resourceId() != null) return channels(tenantId, userId, storeId);
            throw new StoreException("COMMON-1010", 409);
        }
        try {
            idem.insert(tenantId, "setDefaultStoreChannel", key, hash, "PUT", "/api/v1/stores/" + storeId + "/default-logistics-channel", now().plusMinutes(30));
            StoreChannel current = resourceMapper.findChannel(tenantId, storeId, request.channelId());
            resourceMapper.clearOtherDefaultChannels(tenantId, storeId, request.channelId());
            if (current == null) resourceMapper.insertChannel(tenantId, storeId, request.channelId(), userId);
            else if (resourceMapper.updateChannelDefault(tenantId, storeId, request.channelId(), request.version(), userId) != 1) throw new StoreException("COMMON-1005", 409);
            idem.complete(tenantId, "setDefaultStoreChannel", key, request.channelId());
            audit.insert(tenantId, userId, "STORE_CHANNEL_UPDATE", storeId, requestId, now());
            return channels(tenantId, userId, storeId);
        } catch (DuplicateKeyException exception) {
            throw new StoreException("COMMON-1005", 409);
        }
    }

    @Transactional
    public Store update(Long tenantId, Long userId, Long storeId, UpdateStoreRequest request, String key, String requestId) {
        checkKey(key); Store existing = requiredForUser(tenantId, userId, storeId);
        String hash = hash(storeId + "\n" + request.storeName() + "\n" + request.platformCode() + "\n" + request.platformAccount() + "\n" + request.version());
        var prior = idem.find(tenantId, "updateStore", key);
        if (prior != null) { if (!hash.equals(prior.requestHash())) throw new StoreException("COMMON-1009", 409); if (prior.resourceId() != null) return requiredForUser(tenantId, userId, prior.resourceId()); throw new StoreException("COMMON-1010", 409); }
        requireManage(tenantId, userId, storeId);
        if (!"ACTIVE".equals(existing.status())) throw new StoreException("STORE-1002", 422);
        Store duplicate = mapper.findByAccount(tenantId, request.platformCode(), request.platformAccount());
        if (duplicate != null && !duplicate.id().equals(existing.id())) throw new StoreException("STORE-1001", 409);
        try { idem.insert(tenantId, "updateStore", key, hash, "PUT", "/api/v1/stores/" + storeId, now().plusMinutes(30)); if (mapper.update(tenantId, storeId, request.storeName(), request.platformCode(), request.platformAccount(), request.version()) != 1) throw conflict(tenantId, userId, storeId); Store value = requiredForUser(tenantId, userId, storeId); idem.complete(tenantId, "updateStore", key, storeId); audit.insert(tenantId, userId, "UPDATE", storeId, requestId, now()); return value; } catch (DuplicateKeyException exception) { throw new StoreException("COMMON-1010", 409); }
    }

    @Transactional
    public Store status(Long tenantId, Long userId, Long storeId, StoreStatusChangeRequest request, String key, String requestId) {
        if (!Set.of("ACTIVE", "DISABLED").contains(request.status())) throw new StoreException("STORE-1002", 422); checkKey(key); Store existing = requiredForUser(tenantId, userId, storeId);
        String hash = hash(storeId + "\n" + request.status() + "\n" + request.version()); var prior = idem.find(tenantId, "changeStoreStatus", key); if (prior != null) { if (!hash.equals(prior.requestHash())) throw new StoreException("COMMON-1009", 409); if (prior.resourceId() != null) return requiredForUser(tenantId, userId, prior.resourceId()); throw new StoreException("COMMON-1010", 409); }
        requireStatusManage(tenantId, userId, storeId);
        try { idem.insert(tenantId, "changeStoreStatus", key, hash, "POST", "/api/v1/stores/" + storeId + "/status", now().plusMinutes(30)); if (mapper.updateStatus(tenantId, storeId, request.status(), request.version()) != 1) throw conflict(tenantId, userId, storeId); Store value = requiredForUser(tenantId, userId, storeId); idem.complete(tenantId, "changeStoreStatus", key, storeId); audit.insert(tenantId, userId, "STATUS_CHANGE", storeId, requestId, now()); return value; } catch (DuplicateKeyException exception) { throw new StoreException("COMMON-1010", 409); }
    }

    private void requireManage(Long tenantId, Long userId, Long storeId) { requiredForUser(tenantId, userId, storeId); if (scope == null || !scope.canManageStoreConfiguration(tenantId, userId, storeId)) throw new StoreException("COMMON-1004", 403); }
    private void requireStatusManage(Long tenantId, Long userId, Long storeId) { requiredForUser(tenantId, userId, storeId); if (scope == null || !scope.canManageStoreStatus(tenantId, userId, storeId)) throw new StoreException("COMMON-1004", 403); }
    private StoreResourceMapper requireResources() { if (resources == null) throw new IllegalStateException("Store resource mapper is not configured"); return resources; }
    private StoreAddressResponse addressResponse(StoreAddress a) { return new StoreAddressResponse(a.id(), a.tenantId(), a.storeId(), a.addressCode(), a.contactName(), a.companyName(), maskPhone(a.phone()), maskEmail(a.email()), a.countryCode(), a.stateProvince(), a.city(), a.district(), a.addressLine1(), a.addressLine2(), a.postalCode(), a.status(), a.isDefault(), a.version(), a.createdAt(), a.updatedAt()); }
    private StoreChannelResponse channelResponse(StoreChannel c) { return new StoreChannelResponse(c.id(), c.tenantId(), c.storeId(), c.channelId(), c.channelCode(), c.channelName(), c.providerName(), c.status(), c.isDefault(), c.version(), c.createdBy(), c.updatedBy(), c.createdAt(), c.updatedAt()); }
    private String maskPhone(String value) { return value == null ? null : value.length() <= 4 ? "****" : value.substring(0, 2) + "****" + value.substring(value.length() - 2); }
    private String maskEmail(String value) { if (value == null || !value.contains("@")) return value == null ? null : "***"; String[] p = value.split("@", 2); return (p[0].isEmpty() ? "***" : p[0].charAt(0) + "***") + "@" + p[1]; }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private Store required(Long tenantId, Long storeId) { Store s = mapper.findById(tenantId, storeId); if (s == null) throw new StoreException("COMMON-1006", 404); return s; }
    private Store requiredForUser(Long tenantId, Long userId, Long storeId) { Store s = mapper.findByIdForUser(tenantId, userId, storeId); if (s == null) throw new StoreException("COMMON-1006", 404); return s; }
    private StoreException conflict(Long tenantId, Long userId, Long storeId) { return mapper.findByIdForUser(tenantId, userId, storeId) == null ? new StoreException("COMMON-1006", 404) : new StoreException("COMMON-1005", 409); }
    private void checkKey(String key) { if (key == null || key.isBlank() || key.length() > 128) throw new StoreException("COMMON-1001", 400); }
    private String optional(String value, int max) { if (value == null || value.isBlank()) return null; String normalized = value.trim(); if (normalized.length() > max) throw new StoreException("COMMON-1001", 400); return normalized; }
    private String mask(String value) { if (value == null || value.isBlank()) return "未提供"; if (value.length() <= 2) return "*".repeat(value.length()); return value.charAt(0) + "*".repeat(value.length() - 2) + value.charAt(value.length() - 1); }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException(exception); } }
}
