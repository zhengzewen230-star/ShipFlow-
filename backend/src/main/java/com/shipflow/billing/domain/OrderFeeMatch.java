package com.shipflow.billing.domain;

import java.math.BigDecimal;

public record OrderFeeMatch(Long orderId, BigDecimal currentFee, String currency) { }
