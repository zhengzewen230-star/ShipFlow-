package com.shipflow.auth.api;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

@ConfigurationProperties(prefix = "shipflow.auth.cookies")
public class AuthCookieProperties {
    private String refreshName = "REFRESH_TOKEN";
    private String xsrfName = "XSRF-TOKEN";
    private String refreshPath = "/api/v1/auth";
    private String xsrfPath = "/";
    private boolean secure = true;
    private String sameSite = "Strict";
    private Duration refreshMaxAge = Duration.ofDays(30);
    private Duration xsrfMaxAge = Duration.ofHours(1);

    public ResponseCookie refreshCookie(com.shipflow.security.refresh.RefreshToken token) {
        return ResponseCookie.from(refreshName, token.value()).httpOnly(true).secure(secure)
                .sameSite(sameSite).path(refreshPath).maxAge(refreshMaxAge).build();
    }
    public ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(refreshName, "").httpOnly(true).secure(secure)
                .sameSite(sameSite).path(refreshPath).maxAge(Duration.ZERO).build();
    }
    public ResponseCookie clearXsrfCookie() {
        return ResponseCookie.from(xsrfName, "").httpOnly(false).secure(secure)
                .sameSite(sameSite).path(xsrfPath).maxAge(Duration.ZERO).build();
    }
    public ResponseCookie xsrfCookie(String token) {
        return ResponseCookie.from(xsrfName, token).httpOnly(false).secure(secure)
                .sameSite(sameSite).path(xsrfPath).maxAge(xsrfMaxAge).build();
    }
    public String getRefreshName() { return refreshName; }
    public String getXsrfName() { return xsrfName; }
    public String getRefreshPath() { return refreshPath; }
    public String getXsrfPath() { return xsrfPath; }
    public boolean isSecure() { return secure; }
    public String getSameSite() { return sameSite; }
    public Duration getRefreshMaxAge() { return refreshMaxAge; }
    public Duration getXsrfMaxAge() { return xsrfMaxAge; }
    public void setRefreshName(String value) { refreshName = value; }
    public void setXsrfName(String value) { xsrfName = value; }
    public void setRefreshPath(String value) { refreshPath = value; }
    public void setXsrfPath(String value) { xsrfPath = value; }
    public void setSecure(boolean value) { secure = value; }
    public void setSameSite(String value) { sameSite = value; }
    public void setRefreshMaxAge(Duration value) { refreshMaxAge = value; }
    public void setXsrfMaxAge(Duration value) { xsrfMaxAge = value; }
}
