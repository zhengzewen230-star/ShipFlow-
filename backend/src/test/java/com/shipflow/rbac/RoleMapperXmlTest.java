package com.shipflow.rbac;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class RoleMapperXmlTest {
    @Test void mapperUsesTenantScopeAndNoWildcard() throws Exception {
        String xml=Files.readString(Path.of("src/main/resources/mapper/rbac/RoleMapper.xml"), StandardCharsets.UTF_8);
        assertThat(xml.toUpperCase()).doesNotContain("SELECT *");
        assertThat(xml).contains("role_scope='TENANT'", "deleted=0", "version=#{version}", "javaType=\"_long\"");
    }
}
