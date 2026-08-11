package com.shipflow.exceptioncase.domain;
public final class ClaimStateMachine {
    private ClaimStateMachine() { }
    public static boolean canSubmit(String currentStatus) { return "OPEN".equals(currentStatus); }
    public static boolean canResolve(String currentStatus, String targetStatus) {
        return "SUBMITTED".equals(currentStatus)
                && ("APPROVED".equals(targetStatus) || "REJECTED".equals(targetStatus));
    }
    public static boolean canClose(String currentStatus) {
        return "APPROVED".equals(currentStatus) || "REJECTED".equals(currentStatus);
    }
}
