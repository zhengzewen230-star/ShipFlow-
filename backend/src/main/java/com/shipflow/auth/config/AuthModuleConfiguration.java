package com.shipflow.auth.config;

import com.shipflow.auth.api.AuthCookieProperties;
import com.shipflow.auth.application.AuthApplicationService;
import com.shipflow.auth.application.LoginApplicationService;
import com.shipflow.auth.mapper.SysUserMapper;
import com.shipflow.auth.mapper.UserAuthorityMapper;
import com.shipflow.auth.service.LoginIdentityService;
import com.shipflow.auth.service.PasswordAuthenticationService;
import com.shipflow.security.jwt.AccessTokenService;
import com.shipflow.security.jwt.JwtProperties;
import com.shipflow.security.refresh.MyBatisRefreshSessionRepository;
import com.shipflow.security.refresh.RefreshSessionMapper;
import com.shipflow.security.refresh.RefreshSessionRepository;
import com.shipflow.security.refresh.RefreshTokenHmacService;
import com.shipflow.security.refresh.RefreshTokenGenerator;
import com.shipflow.security.refresh.RefreshTokenProperties;
import com.shipflow.security.refresh.RefreshTokenSessionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.time.Clock;
import java.security.SecureRandom;

@Configuration
@EnableConfigurationProperties(AuthCookieProperties.class)
public class AuthModuleConfiguration {
    private static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoO4q7Y8Z9wJ8Q7J6y7G7Q5y2B5H9r1m2e";

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean SecureRandom refreshSecureRandom() { return new SecureRandom(); }
    @Bean RefreshTokenGenerator refreshTokenGenerator(SecureRandom random) { return new RefreshTokenGenerator(random); }
    @Bean
    @ConditionalOnProperty(prefix = "shipflow.security.refresh-token", name = "enabled", havingValue = "true")
    RefreshTokenHmacService refreshTokenHmacService(RefreshTokenProperties properties) {
        return new RefreshTokenHmacService(properties);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    LoginIdentityService loginIdentityService(SysUserMapper users, UserAuthorityMapper authorities,
                                               PasswordEncoder encoder) {
        return new LoginIdentityService(users, authorities, encoder, DUMMY_HASH);
    }

    @Bean
    @ConditionalOnBean({LoginIdentityService.class, AccessTokenService.class})
    LoginApplicationService loginApplicationService(LoginIdentityService identity,
                                                     PasswordEncoder encoder,
                                                     AccessTokenService tokens,
                                                     JwtProperties jwtProperties) {
        return new LoginApplicationService(identity, new PasswordAuthenticationService(encoder, DUMMY_HASH), tokens, jwtProperties);
    }

    @Bean
    @ConditionalOnBean(RefreshSessionMapper.class)
    RefreshSessionRepository refreshSessionRepository(RefreshSessionMapper mapper) {
        return new MyBatisRefreshSessionRepository(mapper);
    }

    @Bean
    @ConditionalOnBean(RefreshSessionRepository.class)
    @ConditionalOnProperty(prefix = "shipflow.security.refresh-token", name = "enabled", havingValue = "true")
    RefreshTokenSessionService refreshTokenSessionService(RefreshSessionRepository repository,
                                                           RefreshTokenGenerator generator,
                                                           RefreshTokenHmacService hmac,
                                                           RefreshTokenProperties properties,
                                                           Clock clock) {
        return new RefreshTokenSessionService(repository, generator, hmac, properties, clock);
    }

    @Bean
    @ConditionalOnBean({LoginApplicationService.class, LoginIdentityService.class,
            AccessTokenService.class, RefreshTokenSessionService.class})
    AuthApplicationService authApplicationService(LoginApplicationService login, LoginIdentityService identity,
                                                  AccessTokenService access, RefreshTokenSessionService refresh,
                                                  JwtProperties jwtProperties) {
        return new AuthApplicationService(login, identity, access, refresh, jwtProperties);
    }
}
