package com.shipflow.operations.domain;

/** Actionable cross-domain work; all values are tenant-isolated counts. */
public record OperationsTodos(long pendingPriceConfirmation, long pendingReconciliation,
                              long activeExceptions, long submittedClaims) { }
