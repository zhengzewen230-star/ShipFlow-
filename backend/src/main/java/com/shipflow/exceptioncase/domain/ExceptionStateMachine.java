package com.shipflow.exceptioncase.domain;
public final class ExceptionStateMachine {
    private ExceptionStateMachine() { }
    public static boolean canAssign(String currentStatus) { return !"CLOSED".equals(currentStatus); }
    public static boolean canTransition(String currentStatus, String targetStatus) {
        return ("OPEN".equals(currentStatus) && "PROCESSING".equals(targetStatus))
                || ("PROCESSING".equals(currentStatus) && "WAITING_PROVIDER_FEEDBACK".equals(targetStatus))
                || ("PROCESSING".equals(currentStatus) && "PENDING_FINANCE_CONFIRMATION".equals(targetStatus))
                || ("WAITING_PROVIDER_FEEDBACK".equals(currentStatus) && "PROCESSING".equals(targetStatus))
                || ("PENDING_FINANCE_CONFIRMATION".equals(currentStatus) && "RESOLVED".equals(targetStatus))
                || ("RESOLVED".equals(currentStatus) && "CLOSED".equals(targetStatus));
    }
}
