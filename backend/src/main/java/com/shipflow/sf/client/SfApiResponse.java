package com.shipflow.sf.client;

public record SfApiResponse(int httpStatus, String requestId, String body, String serviceCode, String businessCode) {
    public SfApiResponse(int httpStatus, String requestId, String body) {
        this(httpStatus, requestId, body, null, null);
    }

    public boolean successfulTransport() {
        return httpStatus >= 200 && httpStatus < 300;
    }
}
