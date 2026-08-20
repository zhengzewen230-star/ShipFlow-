package com.shipflow.warehouse.api.model;

import java.math.BigDecimal;

/** 最新的仓库操作结果；version 用于下一次乐观锁操作。 */
public record WarehouseResult(
        Long orderId,
        String status,
        BigDecimal chargeableWeight,
        BigDecimal currentFee,
        Long version) {
}
