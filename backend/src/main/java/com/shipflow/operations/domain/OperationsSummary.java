package com.shipflow.operations.domain;

/** Tenant-scoped order lifecycle counts for the operations home page. */
public record OperationsSummary(long draft, long pendingInbound, long inbound, long pendingPriceConfirmation,
                                long readyForOutbound, long outbound, long inTransit, long delivered,
                                long exception, long cancelled) { }
