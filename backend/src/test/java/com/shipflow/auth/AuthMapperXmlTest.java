package com.shipflow.auth;

import com.shipflow.auth.mapper.SysUserMapper;
import com.shipflow.auth.mapper.UserAuthorityMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import com.shipflow.store.mapper.StoreMapper;
import com.shipflow.store.mapper.StoreIdempotencyMapper;
import com.shipflow.store.mapper.StoreAuditMapper;
import com.shipflow.user.mapper.UserMapper;
import com.shipflow.user.mapper.UserIdempotencyMapper;
import com.shipflow.user.mapper.UserAuditMapper;
import com.shipflow.rbac.mapper.RoleMapper;
import com.shipflow.rbac.mapper.PermissionMapper;
import com.shipflow.rbac.mapper.RbacAuditMapper;
import com.shipflow.tenant.mapper.TenantMapper;
import com.shipflow.tenant.mapper.TenantProvisioningMapper;
import com.shipflow.tenant.mapper.TenantIdempotencyMapper;
import com.shipflow.tenant.mapper.TenantAuditMapper;
import com.shipflow.logistics.mapper.LogisticsMasterMapper;
import com.shipflow.logistics.mapper.LogisticsIdempotencyMapper;
import com.shipflow.logistics.mapper.LogisticsAuditMapper;
import com.shipflow.quote.mapper.QuoteMapper;
import com.shipflow.quote.mapper.QuoteIdempotencyMapper;
import com.shipflow.quote.mapper.QuotePricingMapper;
import com.shipflow.quote.mapper.QuoteAuditMapper;
import com.shipflow.order.mapper.ShipmentOrderMapper;
import com.shipflow.order.mapper.ShipmentOrderIdempotencyMapper;
import static org.mockito.Mockito.mock;

import java.io.Reader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(AuthMapperXmlTest.MapperXmlConfiguration.class)
class AuthMapperXmlTest {

    private static final List<String> MAPPER_RESOURCES = List.of(
            "mapper/auth/SysUserMapper.xml",
            "mapper/auth/UserAuthorityMapper.xml");

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    void springContextLoadsMapperXmlAndMappedStatements() throws Exception {
        Configuration configuration = sqlSessionFactory.getConfiguration();

        assertThat(configuration.hasStatement(SysUserMapper.class.getName() + ".findPlatformUser")).isTrue();
        assertThat(configuration.hasStatement(SysUserMapper.class.getName() + ".findTenantUser")).isTrue();
        assertThat(configuration.hasStatement(UserAuthorityMapper.class.getName() + ".findActiveAuthorities")).isTrue();
        assertThat(configuration.getResultMap(SysUserMapper.class.getName() + ".sysUserMap")).isNotNull();
        assertThat(configuration.getResultMap(UserAuthorityMapper.class.getName() + ".userAuthorityMap")).isNotNull();
    }

    @Test
    void mapperMethodsAndSqlUseBoundParametersAndExplicitColumns() throws Exception {
        assertThat(SysUserMapper.class.getDeclaredMethod("findPlatformUser", String.class)
                .getParameters()[0].getAnnotation(org.apache.ibatis.annotations.Param.class).value())
                .isEqualTo("username");
        assertThat(SysUserMapper.class.getDeclaredMethod("findTenantUser", String.class, String.class)
                .getParameters()[0].getAnnotation(org.apache.ibatis.annotations.Param.class).value())
                .isEqualTo("tenantCode");

        for (String resource : MAPPER_RESOURCES) {
            String xml = Files.readString(Path.of("src/main/resources", resource), StandardCharsets.UTF_8);
            assertThat(xml.toUpperCase()).doesNotContain("SELECT *");
        }

        String userXml = Files.readString(Path.of("src/main/resources/mapper/auth/SysUserMapper.xml"), StandardCharsets.UTF_8);
        assertThat(userXml).contains("u.tenant_id IS NULL", "u.tenant_id = t.id", "#{tenantCode}", "#{username}");
        assertThat(UserAuthorityMapper.class.getDeclaredMethod("findActiveAuthorities", Long.class, Long.class, String.class)
                .getParameters()).extracting(parameter -> parameter.getAnnotation(org.apache.ibatis.annotations.Param.class).value())
                .containsExactly("userId", "tenantId", "roleScope");
    }

    @TestConfiguration
    static class MapperXmlConfiguration {

        @Bean StoreMapper storeMapper() { return mock(StoreMapper.class); }
        @Bean StoreIdempotencyMapper storeIdempotencyMapper() { return mock(StoreIdempotencyMapper.class); }
        @Bean StoreAuditMapper storeAuditMapper() { return mock(StoreAuditMapper.class); }
        @Bean UserMapper userMapper() { return mock(UserMapper.class); }
        @Bean UserIdempotencyMapper userIdempotencyMapper() { return mock(UserIdempotencyMapper.class); }
        @Bean UserAuditMapper userAuditMapper() { return mock(UserAuditMapper.class); }
        @Bean RoleMapper roleMapper() { return mock(RoleMapper.class); }
        @Bean PermissionMapper permissionMapper() { return mock(PermissionMapper.class); }
        @Bean RbacAuditMapper rbacAuditMapper() { return mock(RbacAuditMapper.class); }
        @Bean TenantMapper tenantMapper() { return mock(TenantMapper.class); }
        @Bean TenantProvisioningMapper tenantProvisioningMapper() { return mock(TenantProvisioningMapper.class); }
        @Bean TenantIdempotencyMapper tenantIdempotencyMapper() { return mock(TenantIdempotencyMapper.class); }
        @Bean TenantAuditMapper tenantAuditMapper() { return mock(TenantAuditMapper.class); }
        @Bean LogisticsMasterMapper logisticsMasterMapper() { return mock(LogisticsMasterMapper.class); }
        @Bean LogisticsIdempotencyMapper logisticsIdempotencyMapper() { return mock(LogisticsIdempotencyMapper.class); }
        @Bean LogisticsAuditMapper logisticsAuditMapper() { return mock(LogisticsAuditMapper.class); }
        @Bean QuoteMapper quoteMapper() { return mock(QuoteMapper.class); }
        @Bean QuoteIdempotencyMapper quoteIdempotencyMapper() { return mock(QuoteIdempotencyMapper.class); }
        @Bean QuotePricingMapper quotePricingMapper() { return mock(QuotePricingMapper.class); }
        @Bean QuoteAuditMapper quoteAuditMapper() { return mock(QuoteAuditMapper.class); }
        @Bean ShipmentOrderMapper shipmentOrderMapper() { return mock(ShipmentOrderMapper.class); }
        @Bean ShipmentOrderIdempotencyMapper shipmentOrderIdempotencyMapper() { return mock(ShipmentOrderIdempotencyMapper.class); }

        @Bean
        SqlSessionFactory sqlSessionFactory() throws Exception {
            Configuration configuration = new Configuration();
            for (String resource : MAPPER_RESOURCES) {
                try (InputStream input = Resources.getResourceAsStream(resource);
                     Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    new XMLMapperBuilder(reader, configuration, resource, configuration.getSqlFragments()).parse();
                }
            }
            return new SqlSessionFactoryBuilder().build(configuration);
        }
    }
}
