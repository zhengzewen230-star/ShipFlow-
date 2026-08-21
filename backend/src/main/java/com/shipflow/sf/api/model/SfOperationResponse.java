package com.shipflow.sf.api.model;

public record SfOperationResponse(
        String operation,
        String serviceCode,
        String requestId,
        String status,
        String errorCode,
        String errorMessage,
        String externalOrderNo,
        String trackingNo,
        String labelUrl,
        String invoiceUrl) {
    public SfOperationResponse(String operation, String serviceCode, String requestId, String status,
                               String errorCode, String errorMessage) {
        this(operation, serviceCode, requestId, status, errorCode, errorMessage, null, null, null, null);
    }
}
