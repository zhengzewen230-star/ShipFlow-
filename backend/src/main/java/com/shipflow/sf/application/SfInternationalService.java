package com.shipflow.sf.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipflow.sf.api.model.SfOperation;
import com.shipflow.sf.api.model.SfOperationResponse;
import com.shipflow.sf.client.SfApiClient;
import com.shipflow.sf.client.SfApiRequest;
import com.shipflow.sf.client.SfApiResponse;
import com.shipflow.sf.config.SfProperties;
import com.shipflow.sf.mapper.SfProviderOrderMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.shipflow.common.trace.TraceId;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SfInternationalService {
    private static final Logger log = LoggerFactory.getLogger(SfInternationalService.class);
    private final SfProperties properties;
    private final SfApiClient client;
    private final Clock clock;
    private final SfProviderOrderMapper providerOrderMapper;
    private final ObjectMapper objectMapper;

    public SfInternationalService(SfProperties properties, SfApiClient client, Clock clock,
                                  SfProviderOrderMapper providerOrderMapper) {
        this(properties, client, clock, providerOrderMapper, new ObjectMapper());
    }

    public SfInternationalService(SfProperties properties, SfApiClient client, Clock clock,
                                  SfProviderOrderMapper providerOrderMapper, ObjectMapper objectMapper) {
        this.properties = properties;
        this.client = client;
        this.clock = clock;
        this.providerOrderMapper = providerOrderMapper;
        this.objectMapper = objectMapper;
    }

    @Autowired
    public SfInternationalService(SfProperties properties, SfApiClient client, Clock clock,
                                  ObjectProvider<SfProviderOrderMapper> providerOrderMapper,
                                  ObjectMapper objectMapper) {
        this(properties, client, clock, providerOrderMapper.getIfAvailable(), objectMapper);
    }

    public SfOperationResponse execute(Long tenantId, Long orderId, SfOperation operation,
                                       String requestId, String msgData) {
        if (tenantId == null || tenantId < 1 || orderId == null || orderId < 1) {
            throw new SfIntegrationException("COMMON-1004", 403, "当前账号无权执行顺丰物流操作");
        }
        if (requestId == null || requestId.isBlank() || requestId.length() > 128) {
            throw new SfIntegrationException("COMMON-1001", 400, "缺少有效的幂等请求标识");
        }
        properties.validateForCall();
        if (providerOrderMapper == null) {
            throw new SfIntegrationException("SF-1011", 422, "顺丰订单状态存储尚未完成迁移");
        }
        SfProviderOrderMapper.Context order = providerOrderMapper.findOrder(tenantId, orderId);
        if (order == null) throw new SfIntegrationException("COMMON-1006", 404, "订单不存在或当前租户无权访问");
        validateOperationState(operation, order.orderStatus(), null);
        if (operation == SfOperation.CREATE_ORDER && !providerOrderMapper.hasMeasurement(tenantId, orderId)) {
            throw new SfIntegrationException("WAREHOUSE-1002", 409, "订单尚未完成复称，不能创建顺丰订单");
        }
        SfProviderOrderMapper.Context existing = providerOrderMapper.findByRequestId(tenantId, requestId);
        if (existing != null) {
            if (!orderId.equals(existing.orderId())) {
                throw new SfIntegrationException("COMMON-1009", 409, "幂等键不能用于不同的顺丰订单");
            }
            if ("PROCESSING".equals(existing.lifecycleStatus())) {
                throw new SfIntegrationException("COMMON-1010", 409, "该顺丰请求正在处理中，请稍后重试");
            }
            return new SfOperationResponse(operation.name(), operation.serviceCode(), requestId,
                    "REPLAYED", null, null, existing.externalOrderNo(), existing.trackingNo(), null, null);
        }
        String previousProviderStatus = null;
        SfProviderOrderMapper.Context provider = null;
        boolean sandboxTest = properties.isSandboxEnabled();
        LocalDateTime testRetentionUntilUtc = sandboxTest
                ? LocalDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC).plusDays(30)
                : null;
        if (operation != SfOperation.CREATE_ORDER) {
            provider = providerOrderMapper.findContext(tenantId, orderId);
            if (provider == null) throw new SfIntegrationException("SF-1008", 409, "顺丰订单尚未创建，不能执行当前操作");
            validateOperationState(operation, order.orderStatus(), provider.lifecycleStatus());
            previousProviderStatus = provider.lifecycleStatus();
        }
        try {
            if (operation == SfOperation.CREATE_ORDER) {
                providerOrderMapper.insertProcessing(tenantId, orderId, order.providerId(), requestId, operation.serviceCode(),
                        sandboxTest, testRetentionUntilUtc);
            } else if (providerOrderMapper.markProcessing(tenantId, orderId, requestId, operation.serviceCode(),
                    sandboxTest, testRetentionUntilUtc) != 1) {
                throw new SfIntegrationException("SF-1010", 409, "当前顺丰订单状态不允许继续操作");
            }
        } catch (DuplicateKeyException duplicate) {
            SfProviderOrderMapper.Context replay = providerOrderMapper.findByRequestId(tenantId, requestId);
            if (replay != null && orderId.equals(replay.orderId())) {
                throw new SfIntegrationException("COMMON-1010", 409, "该顺丰请求正在处理中，请稍后重试");
            }
            throw new SfIntegrationException("COMMON-1009", 409, "幂等键不能用于不同的顺丰订单");
        }

        String payload = buildPayload(tenantId, order, provider, operation, msgData);
        SfApiRequest request = new SfApiRequest(properties.getPartnerId(), requestId,
                operation.serviceCode(), Instant.now(clock).getEpochSecond(), null, payload);
        try {
            SfApiResponse response = client.execute(request);
            ProviderData data = parseProviderData(response.body());
            log.info("sf_business serviceCode={} requestID={} httpStatus={} businessCode={} traceId={} orderId={}",
                    operation.serviceCode(), requestId, response.httpStatus(), response.businessCode(),
                    TraceId.current(), orderId);
            if (!response.successfulTransport() || (response.businessCode() != null && !isSuccessCode(response.businessCode()))) {
                providerOrderMapper.markFailure(tenantId, orderId, requestId,
                        response.businessCode() == null ? "SF-1007" : response.businessCode(),
                        "顺丰服务返回业务失败");
                throw new SfIntegrationException("SF-1007", 503, "顺丰服务返回业务失败，请稍后重试");
            }
            data = completeSandboxReferences(operation, data, orderId, requestId);
            validateCriticalReferences(operation, data);
            providerOrderMapper.markSuccess(tenantId, orderId, requestId, successState(operation, previousProviderStatus),
                    data.externalOrderNo(), data.trackingNo(), data.providerStatus());
            if (operation == SfOperation.PRINT_ORDER && provider != null) {
                providerOrderMapper.upsertLabel(tenantId, orderId, provider.providerOrderId(), requestId,
                        data.labelUrl(), data.invoiceUrl(), "READY", null, null);
            }
            if (operation == SfOperation.UPLOAD_CERTIFY && provider != null) {
                providerOrderMapper.upsertCustomsDocument(tenantId, orderId, provider.providerOrderId(), requestId,
                        "CERTIFY", data.documentReference(), "UPLOADED", null, null);
            }
            return new SfOperationResponse(operation.name(), operation.serviceCode(), requestId,
                    "SUBMITTED", null, null, data.externalOrderNo(), data.trackingNo(), data.labelUrl(), data.invoiceUrl());
        } catch (SfIntegrationException exception) {
            providerOrderMapper.markFailure(tenantId, orderId, requestId, exception.code(), exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            providerOrderMapper.markFailure(tenantId, orderId, requestId, "SF-1007", "顺丰供应商调用失败");
            throw exception;
        }
    }

    private String buildPayload(Long tenantId, SfProviderOrderMapper.Context order,
                                SfProviderOrderMapper.Context provider, SfOperation operation, String supplied) {
        if (operation != SfOperation.CREATE_ORDER) {
            if (supplied == null || supplied.isBlank() || "{}".equals(supplied.trim())) {
                if (operation == SfOperation.UPLOAD_CERTIFY) {
                    throw new SfIntegrationException("SF-1014", 422, "上传清关资料必须提供测试资料报文");
                }
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("customerCode", properties.getCustomerCode());
                payload.put("customerOrderNo", order.orderNo());
                if (provider != null && provider.trackingNo() != null) payload.put("sfWaybillNo", provider.trackingNo());
                return writeJson(payload);
            }
            String canonical = canonicalJson(supplied);
            if (operation == SfOperation.UPLOAD_CERTIFY) {
                try {
                    JsonNode node = objectMapper.readTree(canonical);
                    for (String field : List.of("certName", "certCardNo", "certType", "frontPic", "backPic")) {
                        if (!node.hasNonNull(field) || node.get(field).asText().isBlank()) {
                            throw new SfIntegrationException("SF-1014", 422, "清关资料缺少必填字段");
                        }
                    }
                } catch (SfIntegrationException exception) {
                    throw exception;
                } catch (Exception exception) {
                    throw new SfIntegrationException("SF-1014", 422, "清关资料报文无效");
                }
            }
            return canonical;
        }
        SfProviderOrderMapper.AddressPayload sender = providerOrderMapper.findAddress(tenantId, order.orderId(), "SENDER");
        SfProviderOrderMapper.AddressPayload receiver = providerOrderMapper.findAddress(tenantId, order.orderId(), "RECEIVER");
        List<SfProviderOrderMapper.ItemPayload> items = providerOrderMapper.findItems(tenantId, order.orderId());
        if (sender == null || receiver == null || items == null || items.isEmpty()) {
            throw new SfIntegrationException("SF-1012", 422, "订单地址或商品资料不完整，无法创建顺丰订单");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerCode", properties.getCustomerCode());
        payload.put("customerOrderNo", order.orderNo());
        payload.put("senderInfo", address(sender));
        payload.put("receiverInfo", address(receiver));
        List<Map<String, Object>> cargo = new ArrayList<>();
        for (SfProviderOrderMapper.ItemPayload item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productName", item.productNameEn() == null ? item.productName() : item.productNameEn());
            row.put("quantity", item.quantity());
            row.put("unitPrice", item.unitPrice());
            row.put("currency", item.currency());
            row.put("declaredValue", item.declaredValue());
            row.put("sku", item.sku());
            row.put("originCountry", item.originCountry());
            cargo.add(row);
        }
        payload.put("cargoDetails", cargo);
        return writeJson(payload);
    }

    private Map<String, Object> address(SfProviderOrderMapper.AddressPayload value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contactName", value.contactName());
        result.put("phone", value.phone());
        result.put("companyName", value.companyName());
        result.put("email", value.email());
        result.put("countryCode", value.countryCode());
        result.put("stateProvince", value.stateProvince());
        result.put("city", value.city());
        result.put("district", value.district());
        result.put("addressLine1", value.addressLine1());
        result.put("addressLine2", value.addressLine2());
        result.put("postalCode", value.postalCode());
        return result;
    }

    private String canonicalJson(String raw) {
        try { return objectMapper.writeValueAsString(objectMapper.readTree(raw)); }
        catch (Exception exception) { throw new SfIntegrationException("COMMON-1001", 400, "顺丰业务报文必须是有效JSON"); }
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new SfIntegrationException("SF-1013", 422, "顺丰业务报文生成失败"); }
    }

    private ProviderData parseProviderData(String body) {
        if (body == null || body.isBlank()) return new ProviderData(null, null, null, null, null, null);
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.get("msgData");
            if (data != null && data.isTextual()) data = objectMapper.readTree(data.asText());
            if (data == null || data.isMissingNode() || data.isNull()) data = root;
            return new ProviderData(text(data, "customerOrderNo"), text(data, "sfWaybillNo"),
                    text(data, "status"), text(data, "labelUrl"), text(data, "invoiceUrl"),
                    text(data, "documentReference"));
        } catch (Exception ignored) {
            return new ProviderData(null, null, null, null, null, null);
        }
    }

    private String text(JsonNode node, String name) {
        JsonNode value = node == null ? null : node.get(name);
        return value == null || value.isNull() ? null : value.asText();
    }

    private boolean isSuccessCode(String code) { return "A1000".equalsIgnoreCase(code) || "SUCCESS".equalsIgnoreCase(code) || "0".equals(code); }

    private ProviderData completeSandboxReferences(SfOperation operation, ProviderData data,
                                                   Long orderId, String requestId) {
        if (!properties.isSandboxEnabled()) return data;
        String suffix = requestId.replaceAll("[^A-Za-z0-9]", "");
        if (suffix.length() > 24) suffix = suffix.substring(suffix.length() - 24);
        if (suffix.isBlank()) suffix = String.valueOf(orderId);
        return switch (operation) {
            case CREATE_ORDER -> new ProviderData(
                    blank(data.externalOrderNo()) ? "UAT-SF-ORDER-" + suffix : data.externalOrderNo(),
                    blank(data.trackingNo()) ? "UAT-SF-WAYBILL-" + suffix : data.trackingNo(),
                    blank(data.providerStatus()) ? "UAT_CREATED" : data.providerStatus(),
                    data.labelUrl(), data.invoiceUrl(), data.documentReference());
            case PRINT_ORDER -> new ProviderData(data.externalOrderNo(), data.trackingNo(), data.providerStatus(),
                    blank(data.labelUrl()) ? "http://localhost:5173/0ba564312e4d897ece5d9817c60f4e77.pdf"  : data.labelUrl(),
                    data.invoiceUrl(), data.documentReference());
            case UPLOAD_CERTIFY -> new ProviderData(data.externalOrderNo(), data.trackingNo(), data.providerStatus(),
                    data.labelUrl(), data.invoiceUrl(),
                    blank(data.documentReference()) ? "UAT-SF-CERTIFY-" + suffix : data.documentReference());
            case QUERY_ORDER, CANCEL_ORDER -> data;
        };
    }

    private void validateCriticalReferences(SfOperation operation, ProviderData data) {
        if (!properties.isStrictMode() || properties.isSandboxEnabled()) return;
        String missing = switch (operation) {
            case CREATE_ORDER -> blank(data.trackingNo()) ? "sfWaybillNo" : null;
            case PRINT_ORDER -> blank(data.labelUrl()) ? "labelUrl" : null;
            case UPLOAD_CERTIFY -> blank(data.documentReference()) ? "documentReference" : null;
            case QUERY_ORDER, CANCEL_ORDER -> null;
        };
        if (missing != null) {
            throw new SfBusinessException("SF-1008", "顺丰返回 HTTP 200，但关键业务引用为空: " + missing);
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private void validateOperationState(SfOperation operation, String orderStatus, String providerStatus) {
        if (operation == SfOperation.CREATE_ORDER && !"READY_FOR_OUTBOUND".equals(orderStatus)) {
            throw new SfIntegrationException("WAREHOUSE-1004", 409, "订单完成复称后才能创建顺丰订单");
        }
        if (operation == SfOperation.CANCEL_ORDER && ("OUTBOUND".equals(orderStatus) || "CANCELLED".equals(providerStatus))) {
            throw new SfIntegrationException("SF-1009", 409, "已出库或已取消的顺丰订单不能重复取消");
        }
        if (operation != SfOperation.CREATE_ORDER && ("CANCELLED".equals(providerStatus) || "FAILED".equals(providerStatus))) {
            throw new SfIntegrationException("SF-1010", 409, "当前顺丰订单状态不允许继续操作");
        }
    }

    private String successState(SfOperation operation, String currentProviderStatus) {
        return switch (operation) {
            case CREATE_ORDER -> "CREATED";
            case QUERY_ORDER, UPLOAD_CERTIFY -> currentProviderStatus == null ? "CREATED" : currentProviderStatus;
            case PRINT_ORDER -> "LABEL_READY";
            case CANCEL_ORDER -> "CANCELLED";
        };
    }

    private record ProviderData(String externalOrderNo, String trackingNo, String providerStatus,
                                String labelUrl, String invoiceUrl, String documentReference) { }

    public static String newRequestId() { return "SHIPFLOW-" + UUID.randomUUID(); }
}
