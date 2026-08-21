package com.shipflow.quote.domain.model;

/** Read-only validity result; it never mutates the immutable quote snapshot. */
public record QuoteValidity(boolean valid, String reason) {
}
