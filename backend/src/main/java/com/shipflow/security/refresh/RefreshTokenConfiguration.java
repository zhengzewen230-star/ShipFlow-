package com.shipflow.security.refresh;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.security.SecureRandom;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(RefreshTokenProperties.class)
public class RefreshTokenConfiguration {

    @Bean
    SecureRandom refreshSecureRandom() {
        return new SecureRandom();
    }

    @Bean
    RefreshTokenGenerator refreshTokenGenerator(SecureRandom random) {
        return new RefreshTokenGenerator(random);
    }

    @Bean
    @ConditionalOnProperty(prefix = "shipflow.security.refresh-token", name = "enabled", havingValue = "true")
    RefreshTokenHmacService refreshTokenHmacService(RefreshTokenProperties properties) {
        return new RefreshTokenHmacService(properties);
    }

    @Bean
    @Profile("!test")
    MyBatisRefreshSessionRepository refreshSessionRepository(RefreshSessionMapper mapper) {
        return new MyBatisRefreshSessionRepository(mapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "shipflow.security.refresh-token", name = "enabled", havingValue = "true")
    RefreshTokenSessionService refreshTokenSessionService(
            RefreshSessionRepository repository,
            RefreshTokenGenerator generator,
            RefreshTokenHmacService hmac,
            RefreshTokenProperties properties,
            Clock clock) {
        return new RefreshTokenSessionService(repository, generator, hmac, properties, clock);
    }
}
