package com.shipflow.tenant;

import com.shipflow.tenant.mapper.TenantMapper;
import com.shipflow.tenant.mapper.TenantProvisioningMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TenantMapperXmlTest {
    private static final List<String> FILES = List.of(
            "src/main/resources/mapper/tenant/TenantMapper.xml",
            "src/main/resources/mapper/tenant/TenantProvisioningMapper.xml",
            "src/main/resources/mapper/tenant/TenantAuditMapper.xml",
            "src/main/resources/mapper/tenant/TenantIdempotencyMapper.xml");

    @Test
    void mapperXmlParsesAndUsesSafeSql() throws Exception {
        Configuration configuration = new Configuration();
        for (String file : FILES) {
            try (Reader reader = Files.newBufferedReader(Path.of(file), StandardCharsets.UTF_8)) {
                new XMLMapperBuilder(reader, configuration, file, configuration.getSqlFragments()).parse();
            }
            String xml = Files.readString(Path.of(file), StandardCharsets.UTF_8);
            assertThat(xml.toUpperCase()).doesNotContain("SELECT *");
        }
        assertThat(configuration.hasStatement(TenantMapper.class.getName() + ".findById")).isTrue();
        assertThat(configuration.hasStatement(TenantMapper.class.getName() + ".updateName")).isTrue();
        assertThat(configuration.hasStatement(TenantProvisioningMapper.class.getName() + ".bindAdmin")).isTrue();
        String tenantXml = Files.readString(Path.of(FILES.get(0)), StandardCharsets.UTF_8);
        assertThat(tenantXml).contains("deleted=0", "version=version+1", "version=#{version}",
                "<arg column=\"version\" javaType=\"_long\"/>");
        assertThat(tenantXml).doesNotContain("useGeneratedKeys", "keyProperty");
        String provisioningXml = Files.readString(Path.of(FILES.get(1)), StandardCharsets.UTF_8);
        assertThat(provisioningXml).contains("findPermissionIds", "bindRolePermissions", "NOT EXISTS");
        String idempotencyXml = Files.readString(Path.of(FILES.get(3)), StandardCharsets.UTF_8);
        assertThat(idempotencyXml).contains(
                "<arg column=\"request_hash\" javaType=\"java.lang.String\"/>",
                "<arg column=\"resource_id\" javaType=\"java.lang.Long\"/>",
                "<arg column=\"processing_status\" javaType=\"java.lang.String\"/>");
        String migration = Files.readString(Path.of("..", "database", "migrations", "V005__add_tenant_rbac_permissions.sql"), StandardCharsets.UTF_8);
        assertThat(migration).contains("MERCHANT_ADMIN", "MERCHANT_OPERATOR", "store:read")
                .contains("role_scope = 'TENANT'", "tenant_id IS NOT NULL", "r.deleted = 0", "r.status = 'ACTIVE'")
                .contains("NOT EXISTS")
                .doesNotContain("JOIN sys_permission p ON p.permission_code IN ('tenant:create', 'tenant:read', 'tenant:manage')");
        String repairMigration = Files.readString(Path.of("..", "database", "migrations", "V009__repair_merchant_admin_logistics_read_permission.sql"), StandardCharsets.UTF_8);
        assertThat(repairMigration).contains("MERCHANT_ADMIN", "logistics:read", "role_scope = 'TENANT'",
                        "tenant_id IS NOT NULL", "r.deleted = 0", "r.status = 'ACTIVE'", "NOT EXISTS")
                .doesNotContain("INSERT INTO sys_permission");
        String ensureMigration = Files.readString(Path.of("..", "database", "migrations", "V010__ensure_merchant_admin_logistics_read_permission.sql"), StandardCharsets.UTF_8);
        assertThat(ensureMigration).contains("INSERT INTO sys_permission", "logistics:read", "MERCHANT_ADMIN",
                        "role_scope = 'TENANT'", "tenant_id IS NOT NULL", "r.deleted = 0", "r.status = 'ACTIVE'", "NOT EXISTS")
                .doesNotContain("UPDATE sys_role_permission", "DELETE FROM");
    }
}
