package com.shipflow.sf.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SfProviderOrderMapper {
    record Context(Long providerId, Long providerOrderId, Long orderId, String orderStatus,
                   String lifecycleStatus, String orderNo, String externalOrderNo, String trackingNo) {
        public Context(Long providerId, Long orderId, String orderStatus, String lifecycleStatus) {
            this(providerId, null, orderId, orderStatus, lifecycleStatus, null, null, null);
        }
    }

    record AddressPayload(String contactName, String phone, String companyName, String email,
                          String countryCode, String stateProvince, String city, String district,
                          String addressLine1, String addressLine2, String postalCode) { }

    record ItemPayload(String productName, String productNameEn, Integer quantity, BigDecimal unitPrice,
                       String currency, BigDecimal declaredValue, String sku, String originCountry) { }

    Context findOrder(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    boolean hasMeasurement(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    Context findContext(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    Context findByRequestId(@Param("tenantId") Long tenantId, @Param("requestId") String requestId);
    AddressPayload findAddress(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                               @Param("addressType") String addressType);
    List<ItemPayload> findItems(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);

    int insertProcessing(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                         @Param("providerId") Long providerId, @Param("requestId") String requestId,
                         @Param("serviceCode") String serviceCode, @Param("testFlag") boolean testFlag,
                         @Param("testRetentionUntilUtc") LocalDateTime testRetentionUntilUtc);
    int markProcessing(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                       @Param("requestId") String requestId, @Param("serviceCode") String serviceCode,
                       @Param("testFlag") boolean testFlag,
                       @Param("testRetentionUntilUtc") LocalDateTime testRetentionUntilUtc);
    int markSuccess(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                    @Param("requestId") String requestId, @Param("lifecycleStatus") String lifecycleStatus,
                    @Param("externalOrderNo") String externalOrderNo, @Param("trackingNo") String trackingNo,
                    @Param("providerStatus") String providerStatus);
    int markFailure(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                    @Param("requestId") String requestId, @Param("errorCode") String errorCode,
                    @Param("errorSummary") String errorSummary);
    int upsertLabel(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                    @Param("providerOrderId") Long providerOrderId, @Param("requestId") String requestId,
                    @Param("labelReference") String labelReference, @Param("invoiceReference") String invoiceReference,
                    @Param("status") String status,
                    @Param("errorCode") String errorCode, @Param("errorSummary") String errorSummary);
    int upsertCustomsDocument(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                              @Param("providerOrderId") Long providerOrderId, @Param("requestId") String requestId,
                              @Param("documentType") String documentType, @Param("reference") String reference,
                              @Param("status") String status, @Param("errorCode") String errorCode,
                              @Param("errorSummary") String errorSummary);
}
