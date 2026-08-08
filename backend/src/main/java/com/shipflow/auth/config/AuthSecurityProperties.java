package com.shipflow.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shipflow.auth.security")
public class AuthSecurityProperties {

    private String dummyPasswordHash = "$2a$10$7EqJtq98hPqEX7fNZaFWoO4q7Y8Z9wJ8Q7J6y7G7Q5y2B5H9r1m2e";

    public String getDummyPasswordHash() {
        return dummyPasswordHash;
    }

    public void setDummyPasswordHash(String dummyPasswordHash) {
        this.dummyPasswordHash = dummyPasswordHash;
    }
}
