package com.shipflow.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class InitDataPasswordContractTest {
    private static final Pattern BCRYPT = Pattern.compile("\\$2a\\$10\\$[./A-Za-z0-9]{53}");
    private static final String EXPECTED_TEST_HASH = "$2a$10$SAdx34roAZNYsuH0jyCjeu0D3iF/nBikNAQfFNDpt.hErC8HJG/l.";

    @Test
    void initDataUsesTheValidatedBcryptContractHash() throws Exception {
        String sql = Files.readString(Path.of("..", "database", "init_data.sql"), StandardCharsets.UTF_8);
        String hash = sql.lines()
                .filter(line -> line.contains("SET @test_bcrypt"))
                .map(line -> line.replaceAll(".*'([^']+)'.*", "$1"))
                .findFirst()
                .orElseThrow();

        assertThat(hash).matches(BCRYPT);
        assertThat(hash).doesNotContain("$2a$2a$");
        assertThat(hash).isEqualTo(EXPECTED_TEST_HASH);
        assertThat(new BCryptPasswordEncoder().matches("123456", hash)).isTrue();
    }
}
