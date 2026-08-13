package com.shipflow.auth;

import com.shipflow.auth.api.AuthController;
import com.shipflow.auth.application.AuthApplicationService;
import com.shipflow.auth.mapper.SysUserMapper;
import com.shipflow.auth.mapper.UserAuthorityMapper;
import com.shipflow.security.refresh.RefreshSessionMapper;
import com.shipflow.security.authorization.CurrentCallerService;
import com.shipflow.tenant.mapper.TenantMapper;
import com.shipflow.tenant.mapper.TenantProvisioningMapper;
import com.shipflow.tenant.mapper.TenantIdempotencyMapper;
import com.shipflow.tenant.mapper.TenantAuditMapper;
import com.shipflow.store.mapper.StoreMapper;
import com.shipflow.store.mapper.StoreIdempotencyMapper;
import com.shipflow.store.mapper.StoreAuditMapper;
import com.shipflow.user.mapper.UserMapper;
import com.shipflow.user.mapper.UserIdempotencyMapper;
import com.shipflow.user.mapper.UserAuditMapper;
import com.shipflow.rbac.mapper.RoleMapper;
import com.shipflow.rbac.mapper.PermissionMapper;
import com.shipflow.rbac.mapper.RbacAuditMapper;
import com.shipflow.logistics.mapper.LogisticsMasterMapper;
import com.shipflow.logistics.mapper.LogisticsIdempotencyMapper;
import com.shipflow.logistics.mapper.LogisticsAuditMapper;
import com.shipflow.quote.mapper.QuoteMapper;
import com.shipflow.quote.mapper.QuoteIdempotencyMapper;
import com.shipflow.quote.mapper.QuotePricingMapper;
import com.shipflow.quote.mapper.QuoteAuditMapper;
import com.shipflow.order.mapper.ShipmentOrderMapper;
import com.shipflow.order.mapper.ShipmentOrderIdempotencyMapper;
import com.shipflow.warehouse.mapper.WarehouseMapper;
import com.shipflow.tracking.mapper.TrackingQueryMapper;
import com.shipflow.tracking.mapper.TrackingCallbackMapper;
import com.shipflow.exceptioncase.mapper.ExceptionClaimMapper;
import com.shipflow.billing.mapper.BillingMapper;
import com.shipflow.audit.mapper.AuditQueryMapper;
import com.shipflow.operations.mapper.OperationsMapper;
import com.shipflow.onboarding.mapper.OnboardingMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "shipflow.security.jwt.enabled=false",
        "shipflow.security.refresh-token.enabled=true",
        "shipflow.security.refresh-token.hmac-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
})
@Import(AuthBeanRegistrationTest.TestDependencies.class)
class AuthBeanRegistrationTest {

    private final org.springframework.context.ApplicationContext context;

    AuthBeanRegistrationTest(org.springframework.context.ApplicationContext context) {
        this.context = context;
    }

    @Test
    void componentScanRegistersApplicationServiceAndControllerCanBeConstructed() {
        assertThat(context.getBean(AuthApplicationService.class)).isNotNull();
        assertThat(context.getBean(AuthController.class)).isNotNull();
        assertThat(context.getBean(CurrentCallerService.class)).isNotNull();
    }

    @TestConfiguration
    static class TestDependencies {
        @Bean SysUserMapper sysUserMapper() { return mock(SysUserMapper.class); }
        @Bean UserAuthorityMapper userAuthorityMapper() { return mock(UserAuthorityMapper.class); }
        @Bean RefreshSessionMapper refreshSessionMapper() { return mock(RefreshSessionMapper.class); }
        @Bean TenantMapper tenantMapper() { return mock(TenantMapper.class); }
        @Bean TenantProvisioningMapper tenantProvisioningMapper() { return mock(TenantProvisioningMapper.class); }
        @Bean TenantIdempotencyMapper tenantIdempotencyMapper() { return mock(TenantIdempotencyMapper.class); }
        @Bean TenantAuditMapper tenantAuditMapper() { return mock(TenantAuditMapper.class); }
        @Bean StoreMapper storeMapper() { return mock(StoreMapper.class); }
        @Bean StoreIdempotencyMapper storeIdempotencyMapper() { return mock(StoreIdempotencyMapper.class); }
        @Bean StoreAuditMapper storeAuditMapper() { return mock(StoreAuditMapper.class); }
        @Bean UserMapper userMapper() { return mock(UserMapper.class); }
        @Bean UserIdempotencyMapper userIdempotencyMapper() { return mock(UserIdempotencyMapper.class); }
        @Bean UserAuditMapper userAuditMapper() { return mock(UserAuditMapper.class); }
        @Bean RoleMapper roleMapper() { return mock(RoleMapper.class); }
        @Bean PermissionMapper permissionMapper() { return mock(PermissionMapper.class); }
        @Bean RbacAuditMapper rbacAuditMapper() { return mock(RbacAuditMapper.class); }
        @Bean LogisticsMasterMapper logisticsMasterMapper() { return mock(LogisticsMasterMapper.class); }
        @Bean LogisticsIdempotencyMapper logisticsIdempotencyMapper() { return mock(LogisticsIdempotencyMapper.class); }
        @Bean LogisticsAuditMapper logisticsAuditMapper() { return mock(LogisticsAuditMapper.class); }
        @Bean QuoteMapper quoteMapper() { return mock(QuoteMapper.class); }
        @Bean QuoteIdempotencyMapper quoteIdempotencyMapper() { return mock(QuoteIdempotencyMapper.class); }
        @Bean QuotePricingMapper quotePricingMapper() { return mock(QuotePricingMapper.class); }
        @Bean QuoteAuditMapper quoteAuditMapper() { return mock(QuoteAuditMapper.class); }
        @Bean ShipmentOrderMapper shipmentOrderMapper() { return mock(ShipmentOrderMapper.class); }
        @Bean ShipmentOrderIdempotencyMapper shipmentOrderIdempotencyMapper() { return mock(ShipmentOrderIdempotencyMapper.class); }
        @Bean WarehouseMapper warehouseMapper() { return mock(WarehouseMapper.class); }
        @Bean TrackingQueryMapper trackingQueryMapper() { return mock(TrackingQueryMapper.class); }
        @Bean TrackingCallbackMapper trackingCallbackMapper() { return mock(TrackingCallbackMapper.class); }
        @Bean ExceptionClaimMapper exceptionClaimMapper() { return mock(ExceptionClaimMapper.class); }
        @Bean BillingMapper billingMapper() { return mock(BillingMapper.class); }
        @Bean AuditQueryMapper auditQueryMapper() { return mock(AuditQueryMapper.class); }
        @Bean OperationsMapper operationsMapper() { return mock(OperationsMapper.class); }
        @Bean OnboardingMapper onboardingMapper() { return mock(OnboardingMapper.class); }
        @Bean org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder() {
            try {
                var generator = java.security.KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                var pair = generator.generateKeyPair();
                var key = new com.nimbusds.jose.jwk.RSAKey.Builder((java.security.interfaces.RSAPublicKey) pair.getPublic())
                        .privateKey((java.security.interfaces.RSAPrivateKey) pair.getPrivate())
                        .keyID("test-kid")
                        .build();
                return new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(
                        new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(key)));
            } catch (java.security.GeneralSecurityException exception) {
                throw new IllegalStateException("Test RSA key creation failed", exception);
            }
        }
    }
}
