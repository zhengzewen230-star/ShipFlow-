package com.shipflow.order.mapper;

import java.math.BigDecimal;

public record ShipmentOrderPackageRow(Long id, String packageNo, BigDecimal declaredWeight, BigDecimal declaredLength,
                                      BigDecimal declaredWidth, BigDecimal declaredHeight, BigDecimal volumeWeight,
                                      BigDecimal chargeableWeight, String status) { }
