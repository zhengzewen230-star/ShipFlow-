package com.shipflow.warehouse.api.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record MeasurementRequest(
        @NotNull(message = "实际重量不能为空")
        @DecimalMin(value = "0.001", message = "实际重量必须大于 0")
        @DecimalMax(value = "1000000.000", message = "实际重量不能超过 1,000,000")
        @Digits(integer = 12, fraction = 3, message = "实际重量最多保留 3 位小数且整数部分不超过 12 位")
        BigDecimal actualWeight,
        @NotNull(message = "实际长度不能为空")
        @DecimalMin(value = "0.001", message = "实际长度必须大于 0")
        @DecimalMax(value = "1000000.000", message = "实际长度不能超过 1,000,000")
        @Digits(integer = 12, fraction = 3, message = "实际长度最多保留 3 位小数且整数部分不超过 12 位")
        BigDecimal actualLength,
        @NotNull(message = "实际宽度不能为空")
        @DecimalMin(value = "0.001", message = "实际宽度必须大于 0")
        @DecimalMax(value = "1000000.000", message = "实际宽度不能超过 1,000,000")
        @Digits(integer = 12, fraction = 3, message = "实际宽度最多保留 3 位小数且整数部分不超过 12 位")
        BigDecimal actualWidth,
        @NotNull(message = "实际高度不能为空")
        @DecimalMin(value = "0.001", message = "实际高度必须大于 0")
        @DecimalMax(value = "1000000.000", message = "实际高度不能超过 1,000,000")
        @Digits(integer = 12, fraction = 3, message = "实际高度最多保留 3 位小数且整数部分不超过 12 位")
        BigDecimal actualHeight,
        @NotNull(message = "订单版本不能为空") Long version) {
}
