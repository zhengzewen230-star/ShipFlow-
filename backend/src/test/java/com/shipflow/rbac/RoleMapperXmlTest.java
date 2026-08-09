package com.shipflow.rbac;

import com.shipflow.rbac.mapper.RoleMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RoleMapperXmlTest {

    private static final Path MAPPER = Path.of("src/main/resources/mapper/rbac/RoleMapper.xml");

    @Test
    void roleListOmitsStatusPredicateWhenStatusIsAbsent() throws Exception {
        String xml = Files.readString(MAPPER, StandardCharsets.UTF_8);

        assertThat(xml).contains("<if test=\"status != null and status != ''\">");
        assertThat(xml).contains("AND status=#{status}");
        assertThat(xml).doesNotContain("#{status} IS NULL");
    }

    @Test
    void roleMapperXmlParsesListStatement() throws Exception {
        Configuration configuration = new Configuration();
        try (Reader reader = Files.newBufferedReader(MAPPER, StandardCharsets.UTF_8)) {
            new XMLMapperBuilder(reader, configuration, MAPPER.toString(), configuration.getSqlFragments()).parse();
        }

        assertThat(configuration.hasStatement(RoleMapper.class.getName() + ".list")).isTrue();
    }
}
