package com.shipflow.exceptioncase.application;
public class ExceptionClaimException extends RuntimeException {
    private final String code; private final int status;
    public ExceptionClaimException(String code,int status){super(code);this.code=code;this.status=status;}
    public String code(){return code;} public int status(){return status;}
}
