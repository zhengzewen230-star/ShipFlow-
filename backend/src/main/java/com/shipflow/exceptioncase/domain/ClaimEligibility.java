package com.shipflow.exceptioncase.domain;
public record ClaimEligibility(Long exceptionId, String exceptionStatus, String orderStatus,
                               String orderCurrency) { }
