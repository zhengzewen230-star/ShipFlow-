package com.shipflow.user;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class UserMapperXmlTest {
    @Test void mapperUsesExplicitColumnsAndTenantGuards() throws Exception {
        String xml=Files.readString(Path.of("src/main/resources/mapper/user/UserMapper.xml"), StandardCharsets.UTF_8);
        assertThat(xml.toUpperCase()).doesNotContain("SELECT *");
        assertThat(xml).contains("tenant_id=#{tenantId}", "deleted=0", "version=#{version}", "javaType=\"_long\"");
    }
}
