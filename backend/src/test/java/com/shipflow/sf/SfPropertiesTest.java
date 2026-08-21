package com.shipflow.sf;

import com.shipflow.sf.application.SfIntegrationException;
import com.shipflow.sf.config.SfProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SfPropertiesTest {
    @Test
    void rejectsNonSandboxUrlBeforeAnyNetworkCall() {
        SfProperties properties = ready();
        properties.setApiBaseUrl("https://sfapi.sf-express.com/std/service");
        assertThatThrownBy(properties::validateForCall)
                .isInstanceOf(SfIntegrationException.class)
                .hasMessage("顺丰沙箱未启用或地址不在白名单");
    }

    @Test
    void rejectsIncompleteCredentialWithoutExposingValues() {
        SfProperties properties = ready();
        properties.setCheckWord("");
        assertThatThrownBy(properties::validateForCall)
                .isInstanceOf(SfIntegrationException.class)
                .hasMessage("顺丰沙箱凭据未完成配置");
    }

    private SfProperties ready() {
        SfProperties properties = new SfProperties();
        properties.setApiBaseUrl(SfProperties.SANDBOX_BASE_URL);
        properties.setPartnerId("configured");
        properties.setCheckWord("configured");
        properties.setCustomerCode("configured");
        properties.setMonthlyAccount("configured");
        properties.setSandboxEnabled(true);
        return properties;
    }
}
