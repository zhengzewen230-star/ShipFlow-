package com.shipflow.sf.application;

/** Raised when the provider transport succeeded but the business response is unusable. */
public class SfBusinessException extends SfIntegrationException {
    public SfBusinessException(String code, String message) {
        super(code, 422, message);
    }
}
