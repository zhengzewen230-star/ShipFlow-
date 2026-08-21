package com.shipflow.tracking.domain;

public record ShipmentTrackingOrder(Long id, String orderNo, String waybillNo, Long storeId) {
}
