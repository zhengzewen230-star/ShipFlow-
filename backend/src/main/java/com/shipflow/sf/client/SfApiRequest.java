package com.shipflow.sf.client;

import java.util.LinkedHashMap;
import java.util.Map;

public record SfApiRequest(
        String partnerId,
        String requestId,
        String serviceCode,
        long timestamp,
        String msgDigest,
        String msgData) {

    public SfApiRequest withDigest(String digest) {
        return new SfApiRequest(partnerId, requestId, serviceCode, timestamp, digest, msgData);
    }

    public Map<String, String> formFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("partnerID", partnerId);
        fields.put("requestID", requestId);
        fields.put("serviceCode", serviceCode);
        fields.put("timestamp", Long.toString(timestamp));
        fields.put("msgDigest", msgDigest);
        fields.put("msgData", msgData);
        return fields;
    }
}
