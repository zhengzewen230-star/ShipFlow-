package com.shipflow.sf.api.model;

import java.util.Arrays;

/** The only supplier operations allowed by the ShipFlow adapter. */
public enum SfOperation {
    CREATE_ORDER("COM_RECE_IUOP_CREATE_ORDER"),
    PRINT_ORDER("COM_RECE_IUOP_PRINT_ORDER"),
    QUERY_ORDER("COM_RECE_IUOP_QUERY_ORDER"),
    CANCEL_ORDER("COM_RECE_IUOP_CANCEL_ORDER"),
    UPLOAD_CERTIFY("COM_RECE_IUOP_UPLOAD_CERTIFY");

    private final String serviceCode;

    SfOperation(String serviceCode) {
        this.serviceCode = serviceCode;
    }

    public String serviceCode() {
        return serviceCode;
    }

    public static SfOperation fromPath(String value) {
        return Arrays.stream(values())
                .filter(operation -> operation.name().equalsIgnoreCase(value)
                        || operation.serviceCode().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported SF operation"));
    }
}
