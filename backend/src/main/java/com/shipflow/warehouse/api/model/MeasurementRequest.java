package com.shipflow.warehouse.api.model;
import jakarta.validation.constraints.*;import java.math.BigDecimal;
public record MeasurementRequest(@NotNull @DecimalMin(value="0",inclusive=false) BigDecimal actualWeight,@NotNull @DecimalMin(value="0",inclusive=false) BigDecimal actualLength,@NotNull @DecimalMin(value="0",inclusive=false) BigDecimal actualWidth,@NotNull @DecimalMin(value="0",inclusive=false) BigDecimal actualHeight,@NotNull Long version){}
