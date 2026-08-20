package com.shipflow.sf.config;

import com.shipflow.sf.application.SfIntegrationException;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/** Runtime-only configuration for the SF Open sandbox adapter. */
@ConfigurationProperties(prefix = "shipflow.sf")
public class SfProperties {
    public static final String SANDBOX_BASE_URL = "https://sfapi-sbox.sf-express.com/std/service";

    private String apiBaseUrl;
    private String partnerId;
    private String checkWord;
    private String customerCode;
    private String monthlyAccount;
    private long apiTimeoutMs = 5000;
    private int apiRetryLimit;
    private String callbackSecret;
    private boolean sandboxEnabled;
    private boolean strictMode = true;

    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
    public String getPartnerId() { return partnerId; }
    public void setPartnerId(String partnerId) { this.partnerId = partnerId; }
    public String getCheckWord() { return checkWord; }
    public void setCheckWord(String checkWord) { this.checkWord = checkWord; }
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }
    /** Kept for compatibility with provider-specific configuration; not required by the signed request contract. */
    public String getMonthlyAccount() { return monthlyAccount; }
    public void setMonthlyAccount(String monthlyAccount) { this.monthlyAccount = monthlyAccount; }
    public long getApiTimeoutMs() { return apiTimeoutMs; }
    public void setApiTimeoutMs(long apiTimeoutMs) { this.apiTimeoutMs = apiTimeoutMs; }
    public int getApiRetryLimit() { return apiRetryLimit; }
    public void setApiRetryLimit(int apiRetryLimit) { this.apiRetryLimit = apiRetryLimit; }
    public String getCallbackSecret() { return callbackSecret; }
    public void setCallbackSecret(String callbackSecret) { this.callbackSecret = callbackSecret; }
    public boolean isSandboxEnabled() { return sandboxEnabled; }
    public void setSandboxEnabled(boolean sandboxEnabled) { this.sandboxEnabled = sandboxEnabled; }
    public boolean isStrictMode() { return strictMode; }
    public void setStrictMode(boolean strictMode) { this.strictMode = strictMode; }

    public void validateForCall() {
        if (!sandboxEnabled || !SANDBOX_BASE_URL.equals(apiBaseUrl)) {
            throw notReady("SF-1001", "顺丰沙箱未启用或地址不在白名单");
        }
        if (blank(partnerId) || blank(checkWord) || blank(customerCode)) {
            throw notReady("SF-1002", "顺丰沙箱凭据未完成配置");
        }
        if (apiTimeoutMs < 100 || apiTimeoutMs > 120_000 || apiRetryLimit < 0 || apiRetryLimit > 3) {
            throw notReady("SF-1003", "顺丰接口超时或重试配置无效");
        }
        try {
            URI uri = URI.create(apiBaseUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getQuery() != null
                    || uri.getFragment() != null || uri.getUserInfo() != null
                    || !SANDBOX_BASE_URL.equals(uri.toString())) {
                throw notReady("SF-1001", "顺丰沙箱地址不符合白名单");
            }
        } catch (IllegalArgumentException exception) {
            throw notReady("SF-1001", "顺丰沙箱地址不符合白名单");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static SfIntegrationException notReady(String code, String message) {
        return new SfIntegrationException(code, 422, message);
    }
}
