package com.shipflow.store.application;
public class StoreException extends RuntimeException { private final String code; private final int status; public StoreException(String c,int s){super(c);code=c;status=s;} public String code(){return code;} public int status(){return status;} }
