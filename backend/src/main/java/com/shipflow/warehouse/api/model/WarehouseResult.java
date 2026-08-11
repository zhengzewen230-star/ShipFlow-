package com.shipflow.warehouse.api.model;import java.math.BigDecimal;public record WarehouseResult(Long orderId,String status,BigDecimal chargeableWeight,BigDecimal currentFee){}
