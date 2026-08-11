package com.shipflow.tracking.application;

/** Business rejection whose persisted RETRY event must survive the response exception. */
public class RetainedTrackingCallbackException extends TrackingCallbackException {
    public RetainedTrackingCallbackException(String code, int status) {
        super(code, status);
    }
}
