package com.shipflow.sf.api.model;

/** Optional operation payload. CREATE/PRINT/QUERY/CANCEL are assembled from the tenant order. */
public record SfOperationRequest(String msgData) {
}
