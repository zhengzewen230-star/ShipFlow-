package com.shipflow.audit.application;
public class AuditQueryException extends RuntimeException { private final String code; private final int status; public AuditQueryException(String code,int status){this.code=code;this.status=status;} public String code(){return code;} public int status(){return status;} }
