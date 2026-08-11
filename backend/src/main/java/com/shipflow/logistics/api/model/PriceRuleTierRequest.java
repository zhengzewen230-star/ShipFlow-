package com.shipflow.logistics.api.model;
import com.shipflow.logistics.domain.model.PriceRuleTier.BillingMode; import jakarta.validation.constraints.*; import java.math.BigDecimal;
public record PriceRuleTierRequest(@Min(1) int tierNo, @NotNull @DecimalMin("0.000") BigDecimal minWeight, @DecimalMin("0.001") BigDecimal maxWeight, @NotNull BillingMode billingMode, BigDecimal firstWeight, BigDecimal firstFee, BigDecimal additionalWeight, BigDecimal additionalFee, BigDecimal tierFee) { }
