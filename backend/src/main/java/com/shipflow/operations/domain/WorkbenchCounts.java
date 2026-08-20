package com.shipflow.operations.domain;

public record WorkbenchCounts(
        long pendingOrders, long pendingInbound, long pendingMeasurement, long pendingLabel,
        long pendingOutbound, long inTransit, long trackingException,
        long pendingFeeConfirmation, long billImportErrors, long reconciliationDifference,
        long pendingFinanceReview, long missingAddress, long missingCustomsDocument,
        long pendingWarehouse, long pendingExceptionFollowUp, long pendingReconciliation) {
}
