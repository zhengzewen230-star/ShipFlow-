package com.shipflow.exceptioncase.domain;
public final class ExceptionStateMachine {
    private ExceptionStateMachine() { }
    public static boolean canAssign(String currentStatus) { return "OPEN".equals(currentStatus); }
    public static boolean canTransition(String currentStatus, String targetStatus) {
        return ("PROCESSING".equals(currentStatus) && "RESOLVED".equals(targetStatus))
                || ("RESOLVED".equals(currentStatus) && "CLOSED".equals(targetStatus));
    }
}
