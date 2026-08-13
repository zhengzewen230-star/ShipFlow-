package com.shipflow.onboarding.application;

public class OnboardingException extends RuntimeException {
    private final String code; private final int status;
    public OnboardingException(String code, int status) { super(code); this.code = code; this.status = status; }
    public String code() { return code; } public int status() { return status; }
}
