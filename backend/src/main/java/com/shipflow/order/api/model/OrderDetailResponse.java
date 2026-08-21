package com.shipflow.order.api.model;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Read-only aggregation of facts already owned by order, warehouse, tracking, finance and exception modules. */
public record OrderDetailResponse(@JsonUnwrapped ShipmentOrderResponse order, List<Address> addresses, List<PackageView> packages,
                                  List<FeeAdjustment> feeAdjustments, List<WarehouseRecord> warehouseRecords,
                                  List<TrackingRecord> tracking, List<ExceptionSummary> exceptions,
                                  List<TimelineEvent> timeline) {
    public record Address(String type, String contactName, String companyName, String phone, String email,
                          String countryCode, String stateProvince, String city, String district,
                          String addressLine1, String addressLine2, String postalCode) { }
    public record PackageView(Long id, String packageNo, BigDecimal declaredWeight, BigDecimal declaredLength,
                              BigDecimal declaredWidth, BigDecimal declaredHeight, BigDecimal volumeWeight,
                              BigDecimal chargeableWeight, String status, List<Item> items) { }
    public record Item(Integer itemNo, String sku, String productName, Integer quantity, BigDecimal unitPrice,
                       String currency, BigDecimal declaredValue, String originCountry) { }
    public record FeeAdjustment(Long id, String type, BigDecimal beforeAmount, BigDecimal afterAmount,
                                BigDecimal differenceAmount, String currency, String reason, String status,
                                OffsetDateTime requestedAt, OffsetDateTime confirmedAt) { }
    public record WarehouseRecord(String type, String status, BigDecimal actualWeight, BigDecimal actualLength,
                                  BigDecimal actualWidth, BigDecimal actualHeight, BigDecimal chargeableWeight,
                                  String trackingNo, String location, OffsetDateTime occurredAt) { }
    public record TrackingRecord(String status, String title, String description, String location, String source,
                                 OffsetDateTime occurredAt) { }
    public record ExceptionSummary(Long id, String exceptionNo, String type, String status, String description,
                                   OffsetDateTime reportedAt) { }
    public record TimelineEvent(String eventType, String title, String description, String status, String source,
                                String location, OffsetDateTime occurredAt, String traceId) { }
}
