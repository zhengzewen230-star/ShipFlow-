package com.shipflow.security.refresh;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.Reader;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshSessionMapperXmlTest {

    private static final String RESOURCE = "src/main/resources/mapper/security/RefreshSessionMapper.xml";

    @Test
    void mapperXmlParsesAndStatementsMatchMapperMethods() throws Exception {
        Configuration configuration = new Configuration();
        try (Reader reader = Files.newBufferedReader(Path.of(RESOURCE), StandardCharsets.UTF_8)) {
            new XMLMapperBuilder(reader, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }

        String namespace = RefreshSessionMapper.class.getName();
        assertThat(configuration.hasStatement(namespace + ".insert")).isTrue();
        assertThat(configuration.hasStatement(namespace + ".findByTokenHashForUpdate")).isTrue();
        assertThat(configuration.hasStatement(namespace + ".markActiveAsRotated")).isTrue();
        assertThat(configuration.hasStatement(namespace + ".markExpired")).isTrue();
        assertThat(configuration.hasStatement(namespace + ".revokeFamily")).isTrue();
        assertThat(configuration.hasStatement(namespace + ".findFamily")).isTrue();
        assertThat(configuration.getResultMap(namespace + ".refreshSessionMap")).isNotNull();
    }

    @Test
    void mapperSqlUsesBoundParametersAndRotationSafetyClauses() throws Exception {
        String xml = Files.readString(Path.of(RESOURCE), StandardCharsets.UTF_8).toUpperCase();

        assertThat(xml).doesNotContain("SELECT *", "${");
        assertThat(xml).contains("FOR UPDATE", "STATUS = 'ACTIVE'", "WHERE FAMILY_ID = #{FAMILYID}");
        assertThat(RefreshSessionMapper.class.getDeclaredMethod("findByTokenHashForUpdate", String.class)
                .getParameters()[0].getAnnotation(org.apache.ibatis.annotations.Param.class).value())
                .isEqualTo("tokenHash");
        assertThat(RefreshSessionMapper.class.getDeclaredMethod("revokeFamily", String.class, java.time.LocalDateTime.class)
                .getParameters()).extracting(parameter -> parameter
                        .getAnnotation(org.apache.ibatis.annotations.Param.class).value())
                .containsExactly("familyId", "revokedAt");
    }
}
