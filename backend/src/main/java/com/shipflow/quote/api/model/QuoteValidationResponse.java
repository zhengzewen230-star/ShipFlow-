package com.shipflow.quote.api.model;

/** A found quote is evaluated without changing its immutable snapshot. */
public record QuoteValidationResponse(
        Long quoteId,
        boolean exists,
        boolean expired,
        boolean canCreateOrder,
        String reason) {
}
