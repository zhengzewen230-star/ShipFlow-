package com.shipflow.sf.config;

import com.shipflow.sf.client.DefaultSfApiClient;
import com.shipflow.sf.client.SfApiClient;
import com.shipflow.sf.client.SfSignUtil;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SfProperties.class)
public class SfConfiguration {
    @Bean
    SfSignUtil sfSignUtil() {
        return new SfSignUtil();
    }

    @Bean
    SfApiClient sfApiClient(SfProperties properties, SfSignUtil signUtil) {
        return new DefaultSfApiClient(properties, signUtil);
    }
}
